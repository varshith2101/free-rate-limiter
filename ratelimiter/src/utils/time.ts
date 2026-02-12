import { formatInTimeZone } from "date-fns-tz";

const IST_TZ = "Asia/Kolkata";

export const nowInIst = () => new Date();

export const formatIst = (date: Date) =>
  formatInTimeZone(date, IST_TZ, "yyyy-MM-dd'T'HH:mm:ss");
