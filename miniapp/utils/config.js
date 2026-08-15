const DEFAULT_BASE_URL = 'https://www.jsqyty.com';
const COACH_BASE_URL = 'https://sports.jsqyty.com';

function normalizeBaseUrl(input) {
  const raw = (input || '').trim();
  if (!raw) {
    return DEFAULT_BASE_URL;
  }
  const withProtocol = /^https?:\/\//i.test(raw) ? raw : `https://${raw}`;
  return withProtocol.replace(/\/+$/, '').replace(/\/api$/i, '');
}

module.exports = {
  DEFAULT_BASE_URL,
  COACH_BASE_URL,
  normalizeBaseUrl
};
