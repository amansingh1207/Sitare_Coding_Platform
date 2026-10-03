#!/bin/bash
# deploy.sh - Deploy CodingJudge to production server
# Usage: ./deploy.sh [server_ip] [ssh_user]

set -e

SERVER_IP=${1:-your-server-ip}
SSH_USER=${2:-root}
PROJECT_DIR="/opt/codingjudge"

echo "🚀 Deploying CodingJudge to $SERVER_IP..."

# Check if .env.prod exists locally
if [ ! -f .env.prod ]; then
    echo "❌ Error: .env.prod not found. Copy .env.prod.example to .env.prod and fill in values."
    exit 1
fi

echo "📦 Building backend..."
cd backend
mvn clean package -DskipTests -q
cd ..

echo "📦 Building frontend..."
cd frontend
npm run build
cd ..

echo "📦 Building judge sandbox..."
cd docker/sandbox
docker build -t codingjudge/sandbox:latest .
cd ../..

echo "📦 Creating deployment package..."
tar -czf deploy-package.tar.gz \
    --exclude='.git' \
    --exclude='node_modules' \
    --exclude='target' \
    --exclude='.gitignore' \
    --exclude='*.log' \
    --exclude='*.txt' \
    --exclude='*.md' \
    backend/target/codingjudge-backend-0.1.0.jar \
    frontend/dist \
    docker/sandbox/Dockerfile \
    docker-compose.prod.yml \
    .env.prod \
    nginx/ \
    certbot/

echo "📤 Copying to server..."
scp deploy-package.tar.gz $SSH_USER@$SERVER_IP:/tmp/
scp .env.prod $SSH_USER@$SERVER_IP:/tmp/

echo "🔧 Deploying on server..."
ssh $SSH_USER@$SERVER_IP << 'ENDSSH'
    set -e
    
    # Create project directory
    mkdir -p /opt/codingjudge
    cd /opt/codingjudge
    
    # Extract deployment package
    tar -xzf /tmp/deploy-package.tar.gz
    
    # Move env file
    mv /tmp/.env.prod .env.prod
    
    # Setup nginx and certbot directories
    mkdir -p nginx/conf.d certbot/conf certbot/www
    
    # Stop existing containers
    docker compose -f docker-compose.prod.yml down --remove-orphans || true
    
    # Pull latest images
    docker compose -f docker-compose.prod.yml pull postgres nginx certbot/certbot || true
    
    # Build and start services
    docker compose -f docker-compose.prod.yml up -d --build
    
    # Wait for services to be healthy
    echo "⏳ Waiting for services to start..."
    sleep 30
    
    # Check health
    docker compose -f docker-compose.prod.yml ps
    
    # Check backend health
    for i in {1..10}; do
        if curl -f http://localhost:8080/api/health >/dev/null 2>&1; then
            echo "✅ Backend is healthy"
            break
        fi
        echo "Waiting for backend... ($i/10)"
        sleep 5
    done
    
    echo "✅ Deployment complete!"
ENDSSH

echo "🧹 Cleaning up..."
rm deploy-package.tar.gz

echo "✅ Deployment complete! Access at https://your-domain.com"
echo "📝 Next steps:"
echo "   1. Point your domain DNS to this server's IP"
echo "   2. Run initial certbot: docker compose -f docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot -d your-domain.com"
echo "   3. Restart nginx: docker compose -f docker-compose.prod.yml restart nginx"