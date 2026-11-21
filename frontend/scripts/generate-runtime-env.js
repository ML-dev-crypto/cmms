#!/usr/bin/env node
/*
 * Single runtime configuration mechanism for the frontend.
 *
 * The app reads window.__RUNTIME_CONFIG__ (src/config.ts, public/index.html).
 * This script is used by all three entry points:
 *   - Render static build:  npm run generate:runtime-env
 *   - Docker / Railway:      node /opt/cmms/generate-runtime-env.js --output <html>/runtime-env.js
 *   - Local development:     npm start (falls back to frontend/.env)
 *
 * Configuration is resolved per key in this order:
 *   process.env > --env-path file > DEFAULTS > empty string
 *
 * Only API_URL is required; every other key is an optional integration and
 * receives a safe default so a missing variable can never crash startup.
 * Output contains no values in the logs, only the key count and API host.
 *
 * Usage:
 *   node scripts/generate-runtime-env.js [--output <file>] [--env-path <file>]
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
  HOME_URL: '',
  GOOGLE_KEY: '',
  GOOGLE_TRACKING_ID: '',
  OAUTH2_PROVIDER: '',
  INVITATION_VIA_EMAIL: 'false',
  CLOUD_VERSION: 'false',
  ENABLE_SSO: 'false',
  LOGO_PATHS: '',
  CUSTOM_COLORS: '',
  BRAND_CONFIG: '',
  DEMO_LINK: '',
  PADDLE_SECRET_TOKEN: '',
  PADDLE_ENVIRONMENT: 'production',
  RECAPTCHA_SITE_KEY: '',
  INTERCOM_ID: '',
  CLARITY_ID: '',
  LDAP_ENABLED: 'false',
  SENTRY_DSN: '',
  SENTRY_ENVIRONMENT: 'production',
  SENTRY_RELEASE: 'atlas-frontend'
};

const REQUIRED = ['API_URL'];

const ROOT_DIR = path.join(__dirname, '..');
const DEFAULT_OUTPUT = path.join(ROOT_DIR, 'public', 'runtime-env.js');
const DEFAULT_ENV_FILE = path.join(ROOT_DIR, '.env');

function parseArgs(argv) {
  const options = {};

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const [flag, inlineValue] = arg.split('=');

    if (flag === '--output' || flag === '--env-path') {
      const value = inlineValue !== undefined ? inlineValue : argv[index + 1];
      if (inlineValue === undefined) {
        index += 1;
      }
      if (!value) {
        throw new Error(`Missing value for ${flag}`);
      }
      options[flag.slice(2)] = value;
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return options;
}

function parseEnvFile(contents) {
  const values = {};

  contents.split(/\r?\n/).forEach((rawLine) => {
    const line = rawLine.replace(/^\uFEFF/, '').trim();
    if (!line || line.startsWith('#')) {
      return;
    }

    const separator = line.indexOf('=');
    if (separator === -1) {
      return;
    }

    const key = line.slice(0, separator).trim();
    let value = line.slice(separator + 1).trim();

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    values[key] = value;
  });

  return values;
}

function loadEnvFile(filePath) {
  try {
    return parseEnvFile(fs.readFileSync(filePath, 'utf8'));
  } catch (error) {
    return {};
  }
}

function resolveValue(key, envFileValues) {
  const fromProcess = process.env[key];
  if (fromProcess !== undefined && fromProcess !== '') {
    return fromProcess.trim();
  }

  const fromFile = envFileValues[key];
  if (fromFile !== undefined && fromFile !== '') {
    return fromFile.trim();
  }

  return DEFAULTS[key] !== undefined ? DEFAULTS[key] : '';
}

function normalizeUrl(value) {
  if (value.startsWith('/') || /^https?:\/\//i.test(value)) {
    return value;
  }
  return `https://${value}`;
}

function main() {
  let options;
  try {
    options = parseArgs(process.argv.slice(2));
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }

  const envFilePath = options['env-path']
    ? path.resolve(process.cwd(), options['env-path'])
    : DEFAULT_ENV_FILE;
  const outputPath = options.output
    ? path.resolve(process.cwd(), options.output)
    : DEFAULT_OUTPUT;

  const envFileValues = loadEnvFile(envFilePath);
  const config = {};

  KEYS.forEach((key) => {
    config[key] = resolveValue(key, envFileValues);
  });

  config.API_URL = config.API_URL ? normalizeUrl(config.API_URL) : '';
  config.HOME_URL = config.HOME_URL ? normalizeUrl(config.HOME_URL) : '';

  const missing = REQUIRED.filter((key) => !config[key]);
  if (missing.length > 0) {
    console.error(
      `Missing required frontend environment variables: ${missing.join(', ')}. ` +
        'Set API_URL to the browser-reachable backend origin (for example https://api.example.com/).'
    );
    process.exit(1);
  }

  const contents = `window.__RUNTIME_CONFIG__ = ${JSON.stringify(config, null, 2)};\n`;
  fs.writeFileSync(outputPath, contents, 'utf8');

  const apiHost = /^https?:\/\//i.test(config.API_URL)
    ? new URL(config.API_URL).host
    : 'same-origin';
  console.log(
    `Wrote ${outputPath} (${Object.keys(config).length} keys); API host: ${apiHost}`
  );
}

main();
