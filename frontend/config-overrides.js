// config-overrides.js
const { codeInspectorPlugin } = require('code-inspector-plugin');

module.exports = {
  webpack: function override(config, env) {
    // Opt-in escape hatch for environments where the forked TypeScript checker
    // cannot start (for example newer Node.js releases). Type checking remains
    // covered by `tsc --noEmit`.
    if (process.env.DISABLE_FORK_TS_CHECKER === 'true') {
      config.plugins = config.plugins.filter(
        (plugin) => plugin.constructor.name !== 'ForkTsCheckerWebpackPlugin'
      );
    }
    // Opt-in low memory mode: run minification in the main process instead of
    // spawning one worker per CPU core.
    if (process.env.LOW_MEMORY_BUILD === 'true') {
      (config.optimization.minimizer || []).forEach((minimizer) => {
        if (minimizer && minimizer.options) {
          minimizer.options.parallel = false;
        }
      });
    }
    if (env === 'development') {
      config.plugins.push(
        codeInspectorPlugin({
          bundler: 'webpack',
          editor: 'idea',
          hotKeys: ['altKey']
        })
      );
    }
    if (env === 'production' && process.env.SENTRY_AUTH_TOKEN) {
      const { sentryWebpackPlugin } = require('@sentry/webpack-plugin');
      config.plugins.push(
        sentryWebpackPlugin({
          org: process.env.SENTRY_ORG,
          project: 'frontend',
          authToken: process.env.SENTRY_AUTH_TOKEN,
          release: {
            name:
              process.env.REACT_APP_SENTRY_RELEASE || 'atlas-frontend',
            create: true
          }
        })
      );
    }
    return config;
  },

  devServer: function (configFunction) {
    return function (proxy, allowedHost) {
      const config = configFunction(proxy, allowedHost);
      config.client = {
        ...config.client,
        overlay: false
      };

      return config;
    };
  }
};
