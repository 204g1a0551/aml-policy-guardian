# Stage 1: Build Angular SPA
FROM node:22-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# Stage 2: Hardened Nginx production runtime
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# Clean default nginx static files
RUN rm -rf ./*

# Copy compiled SPA assets and optimized nginx proxy configuration
COPY --from=builder /app/dist/aml-ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
