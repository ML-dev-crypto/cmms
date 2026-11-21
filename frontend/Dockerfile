FROM node:22 AS build

WORKDIR /usr/src/app

COPY package*.json ./

RUN npm config set fetch-retry-maxtimeout 6000000 \
    && npm config set fetch-retry-mintimeout 1000000 \
    && npm ci --legacy-peer-deps
COPY . .

RUN NODE_OPTIONS="--max_old_space_size=4096" npm run build

FROM nginx:1.27.0-alpine
COPY --from=build /usr/src/app/build /usr/share/nginx/html
COPY --from=build /usr/src/app/nginx-custom.conf /etc/nginx/templates/default.conf.template
COPY --from=build /usr/src/app/scripts/generate-runtime-env.js /opt/cmms/generate-runtime-env.js

# The official nginx entrypoint only runs docker-entrypoint.d hooks when the
# command is nginx, so the runtime config is generated there. A missing API_URL
# aborts startup with a clear log instead of serving a broken bundle.
# ${PORT} in the nginx template is filled by the image's envsubst step;
# Railway injects it, and 3000 is used when the platform does not.
RUN apk add --no-cache nodejs \
    && printf '%s\n' \
      '#!/bin/sh' \
      'set -e' \
      'node /opt/cmms/generate-runtime-env.js --output /usr/share/nginx/html/runtime-env.js' \
      > /docker-entrypoint.d/40-generate-runtime-env.sh \
    && chmod +x /docker-entrypoint.d/40-generate-runtime-env.sh
ENV PORT=3000

WORKDIR /usr/share/nginx/html
EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
