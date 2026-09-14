FROM node:22-alpine
WORKDIR /app
COPY backend/package.json ./package.json
COPY backend/server.mjs ./server.mjs
ENV NODE_ENV=production
EXPOSE 8080
CMD ["node", "server.mjs"]
