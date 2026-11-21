#!/usr/bin/env node
/*
 * Generates public/runtime-env.js before a deployment build.
 *
 * The React app reads configuration from window.__RUNTIME_CONFIG__ (see
 * src/config.ts and public/index.html). runtime-env-cra is used when serving
 * the Docker image, but a static host needs the file generated at build time.
 *
 * Values come from process.env. API_URL and HOME_URL are required so a
 * misconfigured deployment fails during the build instead of silently pointing
 * users at localhost. No values are printed, only key names.
 */
const fs = require('fs');
const path = require('path');

const KEYS = [
  'NODE_ENV',
  'API_URL',
  'HOME_URL',
  'GOOGLE_KEY',
  'GOOGLE_TRACKING_ID',
  'OAUTH2_PROVIDER',
  'INVITATION_VIA_EMAIL',
  'CLOUD_VERSION',
  'ENABLE_SSO',
  'LOGO_PATHS',
  'CUSTOM_COLORS',
  'BRAND_CONFIG',
  'DEMO_LINK',
  'PADDLE_SECRET_TOKEN',
  'PADDLE_ENVIRONMENT',
  'RECAPTCHA_SITE_KEY',
  'INTERCOM_ID',
  'CLARITY_ID',
  'LDAP_ENABLED',
  'SENTRY_DSN',
  'SENTRY_ENVIRONMENT',
  'SENTRY_RELEASE'
];

const DEFAULTS = {
  NODE_ENV: 'production',
  INVITATION_VIA_EMAIL: 'false',
  CLOUD_VERSION: 'false',
  ENABLE_SSO: 'false',
  LDAP_ENABLED: 'false',
  PADDLE_ENVIRONMENT: 'production',
  SENTRY_ENVIRONMENT: 'production',
  SENTRY_RELEASE: 'atlas-frontend'
};

const REQUIRED = ['API_URL', 'HOME_URL'];

function normalizeUrl(value) {
  const trimmed = value.trim();
  if (trimmed.startsWith('/') || /^https?:\/\//i.test(trimmed)) {
    return trimmed;
  }
  return `https://${trimmed}`;
}

const config = {};
const missing = [];

KEYS.forEach((key) => {
  const raw = process.env[key];
  if (raw !== undefined && raw !== '') {
    config[key] = key === 'API_URL' || key === 'HOME_URL' ? normalizeUrl(raw) : raw.trim();
  } else if (DEFAULTS[key] !== undefined) {
    config[key] = DEFAULTS[key];
  } else {
    config[key] = '';
    if (REQUIRED.includes(key)) {
      missing.push(key);
    }
  }
});

if (missing.length > 0) {
  console.error(
    `Missing required frontend environment variables: ${missing.join(', ')}`
  );
  process.exit(1);
}

const outputPath = path.join(__dirname, '..', 'public', 'runtime-env.js');
const contents = `window.__RUNTIME_CONFIG__ = ${JSON.stringify(
  config,
  null,
  2
)};\n`;

fs.writeFileSync(outputPath, contents, 'utf8');
const apiHost = /^https?:\/\//i.test(config.API_URL)
  ? new URL(config.API_URL).host
  : 'same-origin';
console.log(
  `Wrote public/runtime-env.js (${Object.keys(config).length} keys); API host: ${apiHost}`
);
