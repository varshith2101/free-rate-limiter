import nodemailer from "nodemailer";
import { config } from "../config";

const getTransporter = () => {
  if (!config.mail.host) {
    return null;
  }

  return nodemailer.createTransport({
    host: config.mail.host,
    port: config.mail.port,
    secure: config.mail.secure,
    auth: config.mail.username
      ? {
          user: config.mail.username,
          pass: config.mail.password,
        }
      : undefined,
  });
};

export const sendOtpEmail = async (to: string, otp: string, name: string) => {
  const transporter = getTransporter();

  if (!transporter) {
    if (config.env !== "production") {
      console.warn(`MAIL_HOST is not set. OTP for ${to}: ${otp}`);
    } else {
      console.warn(`MAIL_HOST is not set. OTP email skipped for ${to}`);
    }
    return;
  }

  const subject = "Your Rate Limiter OTP";
  const text = `Hi ${name},\n\nYour verification code is ${otp}. It expires in 10 minutes.\n\nRate Limiter`;

  await transporter.sendMail({
    from: config.mail.from,
    to,
    subject,
    text,
  });
};
