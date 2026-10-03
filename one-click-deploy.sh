#!/bin/bash
# one-click-deploy.sh - Deploy CodingJudge for FREE with one command
# Usage: ./one-click-deploy.sh [oracle|gcp|railway|render] [your-domain.com]

set -e

PROVIDER=${1:-oracle}
DOMAIN=${2:-localhost}

echo "🚀 One-Click FREE Deployment for CodingJudge"
echo "Provider: $PROVIDER | Domain: $DOMAIN"
echo ""

# Check if .env.prod exists
if [ ! -f .env.prod ]; then
    echo "❌ .env.prod not found!"
    echo "Copy .env.prod.example to .env.prod and fill in values:"
    echo "  cp .env.prod.example .env.prod"
    echo "  nano .env.prod"
    exit 1
fi

# Build everything
echo "📦 Building backend..."
cd backend && mvn clean package -DskipTests -q && cd ..

echo "📦 Building frontend..."
cd frontend && npm run build -q && cd ..

echo "📦 Building judge sandbox..."
cd docker/sandbox && docker build -t codingjudge/sandbox:latest . -q && cd ../..

case $PROVIDER in
    oracle)
        echo "🚀 Deploying to Oracle Cloud Free Tier..."
        echo "Run this on your Oracle instance:"
        echo ""
        echo "  # On Oracle server:"
        echo "  curl -fsSL https://raw.githubusercontent.com/amansingh1207/Sitare_Coding_Platform/main/server-setup.sh | sudo bash"
        echo "  cd /opt/codingjudge"
        echo "  docker compose -f docker-compose.prod.yml up -d --build"
        echo ""
        echo "Then get SSL:"
        echo "  docker compose -f docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot -d $DOMAIN"
        echo "  docker compose -f docker-compose.prod.yml restart nginx"
        ;;
    gcp)
        echo "☁️ Deploying to Google Cloud Run..."
        gcloud run deploy codingjudge-backend --source ./backend --platform managed --region us-central1 --allow-unauthenticated
        gcloud run deploy codingjudge-frontend --source ./frontend --platform managed --region us-central1 --allow-unauthenticated
        echo "Set env vars in Cloud Console or: gcloud run services update codingjudge-backend --set-env-vars=..."
        ;;
    railway)
        echo "🚂 Railway deployment:"
        echo "1. Go to railway.app"
        echo "2. Connect GitHub repo: amansingh1207/Sitare_Coding_Platform"
        echo "3. Add PostgreSQL plugin"
        echo "4. Add backend service (Dockerfile)"
        echo "4. Add frontend service (Static)"
        echo "5. Set env vars in Railway dashboard"
        ;;
    render)
        echo "🎨 Render deployment:"
        echo "1. Go to render.com"
        echo "2. Connect GitHub repo"
        echo "2. Add PostgreSQL (free 90 days)"
        echo "3. Create Web Service for backend"
        echo "4. Create Static Site for frontend"
        echo "5. Set env vars"
        ;;
    *)
        echo "Usage: ./one-click-deploy.sh [oracle|gcp|railway|render] [domain.com]"
        echo ""
        echo "Best FREE options:"
        echo "  oracle  - Oracle Cloud Free Tier (Always free, 4 ARM CPUs, 24GB RAM) ⭐"
        echo "  gcp     - Google Cloud Run (2M req/mo free)"
        echo "  railway - Railway.app (\$5/mo credit)"
        echo "  render  - Render.com (sleeps after 15min)"
        exit 1
        ;;
esac

echo ""
echo "✅ Deployment preparation complete!"
echo ""
echo "📋 Next steps:"
echo "1. Point DNS: A record @ -> your-server-ip"
echo "2. Get SSL: docker compose run --rm certbot certonly --webroot -w /var/www/certbot -d $DOMAIN"
echo "3. Test: https://$DOMAIN"
echo ""
echo "📚 Full guide: DEPLOYMENT.md"
echo "📜 History: DEPLOYMENT_HISTORY.md"