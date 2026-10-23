FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# 공개 폼 URL 앞부분. 화면에 공개 URL을 보여줄 때 쓴다(배포 링크가 있으면 링크 응답의 url이 우선).
ARG VITE_PUBLIC_BASE_URL=http://localhost:8081
ENV VITE_PUBLIC_BASE_URL=$VITE_PUBLIC_BASE_URL
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
