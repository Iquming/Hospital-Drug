# 使用极致轻量的 Nginx 镜像作为底座
FROM nginx:alpine

# 把刚才编译好的 dist 文件夹里的所有内容，塞进 Nginx 的网页展示目录
COPY dist/ /usr/share/nginx/html/

# 声明暴露 80 端口
EXPOSE 80