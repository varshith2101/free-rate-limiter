package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.dto.NukeTestRequest;
import com.varshith.ratelimiter.dto.NukeTestStatusResponse;
import com.varshith.ratelimiter.exception.InvalidConfigException;
import com.varshith.ratelimiter.model.Endpoint;
import com.varshith.ratelimiter.repository.EndpointRepository;
import com.varshith.ratelimiter.repository.RateLimitConfigRepository;
import com.varshith.ratelimiter.repository.TenantRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NukeTestService {

    private static final int LOG_LIMIT = 200;

    private final EndpointRepository endpointRepository;
    private final TenantRepository tenantRepository;
    private final RateLimitConfigRepository rateLimitConfigRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ConcurrentHashMap<UUID, NukeTestRun> runs = new ConcurrentHashMap<>();

    public NukeTestService(
            EndpointRepository endpointRepository,
            TenantRepository tenantRepository,
            RateLimitConfigRepository rateLimitConfigRepository) {
        this.endpointRepository = endpointRepository;
        this.tenantRepository = tenantRepository;
        this.rateLimitConfigRepository = rateLimitConfigRepository;
    }

    public UUID startTest(UUID tenantId, UUID endpointId, NukeTestRequest request) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new RuntimeException("Tenant not found");
        }

        Endpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new RuntimeException("Endpoint not found"));

        if (!endpoint.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized");
        }

        if (rateLimitConfigRepository.findActiveByEndpointId(endpointId).isEmpty()) {
            throw new InvalidConfigException("Cannot run nuke test: no active rate limit configuration for this endpoint");
        }

        UUID testId = UUID.randomUUID();
        String endpointUrl = endpoint.getFullUrl();
        String requestUrl = resolveExecutionUrl(endpointUrl);

        NukeTestRun run = new NukeTestRun(testId, endpointUrl, requestUrl, endpoint.getHttpMethod(),
                request.getTotalRequests(), request.getConcurrency());
        runs.put(testId, run);

        Thread worker = new Thread(() -> execute(run));
        worker.setDaemon(true);
        worker.start();

        return testId;
    }

    public NukeTestStatusResponse getStatus(UUID testId) {
        NukeTestRun run = runs.get(testId);
        if (run == null) {
            throw new RuntimeException("Nuke test not found");
        }
        return run.toResponse();
    }

    private void execute(NukeTestRun run) {
        run.markStarted();
        ExecutorService executor = Executors.newFixedThreadPool(run.concurrency);
        CountDownLatch latch = new CountDownLatch(run.totalRequests);

        for (int i = 0; i < run.totalRequests; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                            run.requestUrl,
                            run.resolveMethod(),
                            run.buildEntity(),
                            String.class
                    );
                    int status = response.getStatusCode().value();
                    run.record(status);
                } catch (HttpStatusCodeException ex) {
                    int status = ex.getStatusCode().value();
                    run.record(status);
                } catch (Exception ex) {
                    run.recordError(ex.getClass().getSimpleName() + " at " + run.requestUrl);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            run.recordError("Interrupted");
        } finally {
            executor.shutdown();
            run.markFinished();
        }
    }

    private String resolveExecutionUrl(String endpointUrl) {
        Optional<String> dockerFallback = buildDockerHostFallbackUrl(endpointUrl);
        return dockerFallback.orElse(endpointUrl);
    }

    private Optional<String> buildDockerHostFallbackUrl(String endpointUrl) {
        try {
            java.net.URI uri = new java.net.URI(endpointUrl);
            String host = uri.getHost();
            if (host == null) {
                return Optional.empty();
            }

            boolean isHostLocal = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host);
            if (!isHostLocal) {
                return Optional.empty();
            }

            java.net.URI fallbackUri = new java.net.URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    "host.docker.internal",
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
            return Optional.of(fallbackUri.toString());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static class NukeTestRun {
        private final UUID testId;
        private final String endpointUrl;
        private final String requestUrl;
        private final String httpMethod;
        private final int totalRequests;
        private final int concurrency;
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger accepted = new AtomicInteger();
        private final AtomicInteger rejected = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        private final Deque<String> logs = new ArrayDeque<>();
        private final Object logLock = new Object();
        private String status = "RUNNING";
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;

        private NukeTestRun(UUID testId, String endpointUrl, String requestUrl, String httpMethod, int totalRequests, int concurrency) {
            this.testId = testId;
            this.endpointUrl = endpointUrl;
            this.requestUrl = requestUrl;
            this.httpMethod = httpMethod;
            this.totalRequests = totalRequests;
            this.concurrency = concurrency;
        }

        private void markStarted() {
            startedAt = LocalDateTime.now();
            addLog("Started nuke test: " + totalRequests + " requests with concurrency " + concurrency);
            if (!endpointUrl.equals(requestUrl)) {
                addLog("Running from Docker using: " + requestUrl + " (configured: " + endpointUrl + ")");
            }
        }

        private void markFinished() {
            status = "COMPLETED";
            finishedAt = LocalDateTime.now();
            addLog("Completed nuke test");
        }

        private void record(int statusCode) {
            completed.incrementAndGet();
            if (statusCode == 429) {
                rejected.incrementAndGet();
            } else if (statusCode >= 200 && statusCode < 400) {
                accepted.incrementAndGet();
            } else {
                errors.incrementAndGet();
            }
            addLog("Response " + statusCode + " from " + requestUrl);
        }

        private void recordError(String message) {
            completed.incrementAndGet();
            errors.incrementAndGet();
            addLog("Error: " + message);
        }

        private HttpMethod resolveMethod() {
            if (httpMethod == null || httpMethod.isBlank() || "*".equals(httpMethod)) {
                return HttpMethod.GET;
            }
            return HttpMethod.valueOf(httpMethod);
        }

        private HttpEntity<String> buildEntity() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (resolveMethod() == HttpMethod.GET) {
                return new HttpEntity<>(headers);
            }
            return new HttpEntity<>("{}", headers);
        }

        private void addLog(String entry) {
            synchronized (logLock) {
                logs.addLast(entry);
                while (logs.size() > LOG_LIMIT) {
                    logs.removeFirst();
                }
            }
        }

        private NukeTestStatusResponse toResponse() {
            List<String> logList;
            synchronized (logLock) {
                logList = new ArrayList<>(logs);
            }

            return NukeTestStatusResponse.builder()
                    .testId(testId)
                    .status(status)
                    .endpointUrl(endpointUrl)
                    .httpMethod(httpMethod)
                    .totalRequests(totalRequests)
                    .completedRequests(completed.get())
                    .acceptedRequests(accepted.get())
                    .rejectedRequests(rejected.get())
                    .errorRequests(errors.get())
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .logs(logList)
                    .build();
        }
    }
}
