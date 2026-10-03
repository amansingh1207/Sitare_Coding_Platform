#!/bin/bash
# deploy-free.sh - Deploy CodingJudge to FREE cloud (Oracle Cloud / Google Cloud Run)
# Usage: ./deploy-free.sh [oracle|gcp] [region]

set -e

PROVIDER=${1:-oracle}
REGION=${2:-us-ashburn-1}

echo "🚀 Free Deployment for CodingJudge"
echo "Provider: $PROVIDER | Region: $REGION"
echo ""

if [ "$PROVIDER" = "oracle" ]; then
    echo "📋 Oracle Cloud Free Tier Deployment"
    echo "======================================"
    echo ""
    echo "Prerequisites (do once):"
    echo "1. Sign up at: https://cloud.oracle.com/free"
    echo "2. Create ARM instance: 4 OCPUs, 24GB RAM, Ubuntu 22.04"
    echo "3. Add SSH key, open ports 22, 80, 443"
    echo "4. Note down: Public IP, Private IP"
    echo ""
    echo "Run on your Oracle instance:"
    echo "  curl -fsSL https://raw.githubusercontent.com/amansingh1207/Sitare_Coding_Platform/main/server-setup.sh | sudo bash"
    echo "  cd /opt/codingjudge"
    echo "  cp .env.prod.example .env.prod && nano .env.prod"
    echo "  docker compose -f docker-compose.prod.yml up -d"
    echo ""
    echo "Get SSL cert:"
    echo "  docker compose -f docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot -d yourdomain.com"
    echo "  docker compose -f docker-compose.prod.yml restart nginx"
    
elif [ "$PROVIDER" = "gcp" ]; then
    echo "☁️ Google Cloud Run Deployment (Free Tier)"
    echo "=========================================="
    echo ""
    echo "Free tier: 2M requests/month, 360M vCPU-seconds, 180K GiB-seconds"
    echo ""
    echo "Prerequisites:"
    echo "1. gcloud CLI installed and authenticated"
    echo "2. Project created: gcloud projects create codingjudge-\$RANDOM"
    echo "3. Enable APIs: run, cloudbuild, artifactregistry, sqladmin"
    echo ""
    echo "Deploy commands:"
    echo "  # Backend to Cloud Run"
    echo "  gcloud run deploy codingjudge-backend --source ./backend --platform managed --region $REGION --allow-unauthenticated"
    echo ""
    echo "  # Frontend to Cloud Run"
    echo "  gcloud run deploy codingjudge-frontend --source ./frontend --platform managed --region $REGION --allow-unauthenticated"
    echo ""
    echo "  # Cloud SQL (PostgreSQL) - Free tier: 1 instance, 1GB RAM"
    echo "  gcloud sql instances create codingjudge-db --database-version=POSTGRES_16 --tier=db-f1-micro --region=$REGION"
    echo ""
    echo "  # Connect backend to Cloud SQL"
    echo "  gcloud run services update codingjudge-backend --add-cloudsql-instances=\$PROJECT_ID:$REGION:codingjudge-db"
    echo ""
    echo "  # Set env vars"
    echo "  gcloud run services update codingjudge-backend --set-env-vars=MAIL_USERNAME=...,MAIL_PASSWORD=...,JWT_SECRET=..."
    echo ""
    echo "  # Custom domain"
    echo "  gcloud run domain-mappings create --service=codingjudge-frontend --domain=yourdomain.com --region=$REGION"

elif [ "$PROVIDER" = "railway" ]; then
    echo "🚂 Railway Deployment (Free Tier)"
    echo "================================="
    echo ""
    echo "Free: \$5/month credit (enough for small apps)"
    echo ""
    echo "1. Connect GitHub repo at railway.app"
    echo "2. Add PostgreSQL plugin"
    echo "3. Add services: backend, frontend, postgres"
    echo "4. Set environment variables in Railway dashboard"
    echo "5. Deploy!"

elif [ "$PROVIDER" = "render" ]; then
    echo "🎨 Render Deployment (Free Tier)"
    echo "================================"
    echo ""
    echo "Free: Web services (sleep after 15min inactivity), PostgreSQL (90 days)"
    echo ""
    echo "1. Connect GitHub repo at render.com"
    echo "2. Create PostgreSQL database (free 90 days)"
    echo "3. Create Web Service for backend (Docker)"
    echo "4. Create Static Site for frontend"
    echo "5. Set environment variables"

else
    echo "Usage: ./deploy-free.sh [oracle|gcp|railway|render] [region]"
    echo ""
    echo "Best FREE options:"
    echo "  oracle  - Oracle Cloud Free Tier (Always free, 4 ARM CPUs, 24GB RAM) ⭐ RECOMMENDED"
    echo "  gcp     - Google Cloud Run (2M req/mo free)"
    echo "  railway - Railway.app (\$5/mo credit)"
    echo "  render  - Render.com (sleeps after 15min)"
    exit 1
fi

echo ""
echo "📋 Common Steps for ALL providers:"
echo "=================================="
echo "1. Set up DNS: Point your domain to the provided IP/URL"
echo "2. Configure .env.prod with real values"
echo "3. Get SSL cert (Let's Encrypt - FREE)"
echo "4. Test: https://yourdomain.com"
echo ""
echo "🔑 Required .env.prod values:"
echo "  DB_PASSWORD=strong_random_password"
echo "  JWT_SECRET=256_bit_random_string"
echo "  MAIL_USERNAME=your@gmail.com"
echo "  MAIL_PASSWORD=gmail_app_password"
echo "  DOMAIN=yourdomain.com"
echo ""
echo "📚 Full guide: DEPLOYMENT.md"