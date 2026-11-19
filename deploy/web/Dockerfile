# syntax=docker/dockerfile:1.7
# Front-end React (Vite) servido por nginx. nginx además reenvía /api al API Gateway (misma URL, sin CORS en producción).
# Contexto de construcción = raíz del repositorio (ver deploy/docker-compose.yml).
FROM node:22-alpine AS construccion
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=construccion /web/dist /usr/share/nginx/html
COPY deploy/web/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
