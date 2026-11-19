# asistente-service (Canchita) · Node 22 + TypeScript + SDK de agentes de Claude
# Imagen glibc (bookworm-slim): el SDK trae el binario de Claude Code por plataforma (linux-x64 / linux-arm64).

FROM node:22-bookworm-slim AS construccion
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY tsconfig.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

FROM node:22-bookworm-slim
ENV NODE_ENV=production \
    TZ=America/Lima \
    PORT=8096
WORKDIR /app
COPY --from=construccion --chown=node:node /app/node_modules ./node_modules
COPY --from=construccion --chown=node:node /app/dist ./dist
COPY --chown=node:node package.json ./
# Usuario sin privilegios; Claude Code necesita un HOME escribible para su configuración.
USER node
EXPOSE 8096
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD node -e "fetch('http://localhost:8096/actuator/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "--max-old-space-size=256", "dist/servidor.js"]
