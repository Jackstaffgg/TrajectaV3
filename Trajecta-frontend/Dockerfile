FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_CESIUM_ION_TOKEN=""
ENV VITE_CESIUM_ION_TOKEN=${VITE_CESIUM_ION_TOKEN}

ARG VITE_APP_BASE_PATH="/"
ENV VITE_APP_BASE_PATH=${VITE_APP_BASE_PATH}

ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

ARG VITE_WS_PATH="/ws"
ENV VITE_WS_PATH=${VITE_WS_PATH}

RUN npm run build -- --base=${VITE_APP_BASE_PATH}

# Ensure Cesium runtime assets are shipped with production bundle.
RUN mkdir -p /app/dist/cesium \
	&& cp -R /app/node_modules/cesium/Build/Cesium/* /app/dist/cesium/

FROM nginx:1.27-alpine

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
