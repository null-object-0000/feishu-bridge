FROM node:22-alpine AS builder

WORKDIR /build
COPY package.json tsconfig.json ./
COPY nodes/ ./nodes/
RUN npm install --include=dev && npm run build

FROM n8nio/n8n:latest

USER root

COPY --from=builder /build/package.json /opt/custom-node/package.json
COPY --from=builder /build/dist/ /opt/custom-node/dist/

RUN mkdir -p /home/node/.n8n/nodes && \
    cd /home/node/.n8n/nodes && \
    npm init -y && \
    npm install /opt/custom-node && \
    chown -R node:node /home/node/.n8n

USER node

ENV N8N_SECURE_COOKIE=false

WORKDIR /home/node
