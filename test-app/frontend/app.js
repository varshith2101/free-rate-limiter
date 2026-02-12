const form = document.getElementById('article-form');
const articlesContainer = document.getElementById('articles');
const messageEl = document.getElementById('form-message');
const refreshBtn = document.getElementById('refresh-btn');
const behindGateway =
  window.location.pathname.startsWith('/test-app') &&
  (!window.location.port || window.location.port === '80');
const apiBase = behindGateway
  ? '/test-app/api'
  : '/api';

async function fetchArticles() {
  const res = await fetch(`${apiBase}/articles`);
  if (!res.ok) {
    throw new Error('Failed to fetch articles');
  }
  return res.json();
}

function renderArticles(articles) {
  if (!articles.length) {
    articlesContainer.innerHTML = '<p>No articles yet.</p>';
    return;
  }

  articlesContainer.innerHTML = articles
    .map(
      (article) => `
      <article class="article">
        <h3>${escapeHtml(article.title)}</h3>
        <p>${escapeHtml(article.content)}</p>
        <p class="meta">By ${escapeHtml(article.author || 'unknown')} · ${new Date(article.createdAt).toLocaleString()}</p>
      </article>
    `
    )
    .join('');
}

function setMessage(text, type = '') {
  messageEl.textContent = text;
  messageEl.className = `message ${type}`.trim();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

async function loadAndRender() {
  try {
    const articles = await fetchArticles();
    renderArticles(articles);
  } catch (error) {
    articlesContainer.innerHTML = `<p class="message error">${error.message}</p>`;
  }
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  setMessage('');

  const title = document.getElementById('title').value.trim();
  const content = document.getElementById('content').value.trim();
  const ownerName = document.getElementById('ownerName').value.trim();
  const ownerPassword = document.getElementById('ownerPassword').value;

  try {
    const res = await fetch(`${apiBase}/articles`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-owner-name': ownerName,
        'x-owner-password': ownerPassword,
      },
      body: JSON.stringify({ title, content }),
    });

    const payload = await res.json();

    if (!res.ok) {
      throw new Error(payload.message || 'Failed to create article');
    }

    setMessage('Article created successfully', 'success');
    form.reset();
    await loadAndRender();
  } catch (error) {
    setMessage(error.message, 'error');
  }
});

refreshBtn.addEventListener('click', () => {
  loadAndRender();
});

loadAndRender();
