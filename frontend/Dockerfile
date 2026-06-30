# ===== Stage 1: Node Build =====
FROM node:18-alpine AS builder
WORKDIR /build

# 先复制package.json安装依赖(利用Docker缓存层)
COPY package*.json ./
RUN npm install --registry=https://registry.npmmirror.com

# 复制源码并构建
COPY . .
RUN npm run build

# ===== Stage 2: Nginx Runtime =====
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 复制Nginx配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
