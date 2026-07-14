FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --silent

COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/dist/marketing-app/server ./server
COPY --from=build /app/dist/marketing-app/browser ./browser
COPY --from=build /app/node_modules ./node_modules

EXPOSE 4000
CMD ["node", "server/server.mjs"]
