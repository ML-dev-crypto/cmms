const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const SCRIPT = path.join(__dirname, 'generate-runtime-env.js');

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

function tempDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'runtime-env-'));
}

function run(args, overrides = {}) {
  const env = { ...process.env };
  KEYS.forEach((key) => delete env[key]);
  return spawnSync(process.execPath, [SCRIPT, ...args], {
    env: { ...env, ...overrides },
    encoding: 'utf8'
  });
}

function readConfig(file) {
  const raw = fs.readFileSync(file, 'utf8');
  const match = raw.match(/window\.__RUNTIME_CONFIG__\s*=\s*(\{[\s\S]*\});/);
  assert.ok(match, `unexpected runtime config format:\n${raw}`);
  return JSON.parse(match[1]);
}

test('starts with only API_URL set and defaults every optional key', () => {
  const dir = tempDir();
  const output = path.join(dir, 'runtime-env.js');
  const envFile = path.join(dir, 'missing.env');

  const result = run(['--output', output, '--env-path', envFile], {
    API_URL: 'https://api.example.com/'
  });

  assert.equal(result.status, 0, result.stderr);
  const config = readConfig(output);
  assert.equal(config.API_URL, 'https://api.example.com/');
  assert.equal(config.HOME_URL, '');
  assert.equal(config.GOOGLE_TRACKING_ID, '');
  assert.equal(config.GOOGLE_KEY, '');
  assert.equal(config.INVITATION_VIA_EMAIL, 'false');
  assert.equal(config.ENABLE_SSO, 'false');
  assert.equal(config.NODE_ENV, 'production');
  assert.equal(Object.keys(config).length, KEYS.length);
});

test('fails with an explicit message when API_URL is absent', () => {
  const dir = tempDir();
  const output = path.join(dir, 'runtime-env.js');
  const envFile = path.join(dir, 'missing.env');

  const result = run(['--output', output, '--env-path', envFile]);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /API_URL/);
  assert.equal(fs.existsSync(output), false);
});

test('reads values from an env file', () => {
  const dir = tempDir();
  const output = path.join(dir, 'runtime-env.js');
  const envFile = path.join(dir, 'test.env');
  fs.writeFileSync(
    envFile,
    [
      '# comment',
      'API_URL=https://file-api.example.com/',
      'HOME_URL=https://file-home.example.com',
      'GOOGLE_TRACKING_ID=UA-FROM-FILE',
      'INVITATION_VIA_EMAIL=true',
      'SENTRY_RELEASE='
    ].join('\n')
  );

  const result = run(['--output', output, '--env-path', envFile]);

  assert.equal(result.status, 0, result.stderr);
  const config = readConfig(output);
  assert.equal(config.API_URL, 'https://file-api.example.com/');
  assert.equal(config.HOME_URL, 'https://file-home.example.com');
  assert.equal(config.GOOGLE_TRACKING_ID, 'UA-FROM-FILE');
  assert.equal(config.INVITATION_VIA_EMAIL, 'true');
  assert.equal(config.SENTRY_RELEASE, 'atlas-frontend');
});

test('process environment takes precedence over the env file', () => {
  const dir = tempDir();
  const output = path.join(dir, 'runtime-env.js');
  const envFile = path.join(dir, 'test.env');
  fs.writeFileSync(
    envFile,
    ['API_URL=https://file-api.example.com/', 'GOOGLE_TRACKING_ID=UA-FROM-FILE'].join('\n')
  );

  const result = run(['--output', output, '--env-path', envFile], {
    API_URL: 'https://process-api.example.com/',
    GOOGLE_TRACKING_ID: 'UA-FROM-PROCESS'
  });

  assert.equal(result.status, 0, result.stderr);
  const config = readConfig(output);
  assert.equal(config.API_URL, 'https://process-api.example.com/');
  assert.equal(config.GOOGLE_TRACKING_ID, 'UA-FROM-PROCESS');
});

test('adds a scheme to a bare API_URL host', () => {
  const dir = tempDir();
  const output = path.join(dir, 'runtime-env.js');

  const result = run(['--output', output, '--env-path', path.join(dir, 'missing.env')], {
    API_URL: 'api.example.com'
  });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(readConfig(output).API_URL, 'https://api.example.com');
});
