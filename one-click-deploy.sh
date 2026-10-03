#!/bin/bash
# one-click-deploy.sh - Deploy CodingJudge for FREE with one command
# Usage: ./one-click-deploy.sh [oracle|koyeb|koyeb-supabase|render|gcp|railway] [your-domain.com]

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
        echo "🚀 Deploying to Oracle Cloud Free Tier (NO CREDIT CARD)"
        echo "=========================================================="
        echo ""
        echo "✅ NO CREDIT CARD REQUIRED - Always Free Tier!"
        echo ""
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
        echo ""
        echo "✅ Always free, never sleeps, 4 ARM CPUs, 24GB RAM"
        ;;
    gcp)
        echo "☁️ Google Cloud Run - REQUIRES CREDIT CARD"
        echo "============================================"
        echo ""
        echo "⚠️  GCP REQUIRES credit card for billing account (even for free tier)!"
        echo "    If you don't have a credit card, use 'oracle' or 'koyeb' instead."
        echo ""
        echo "If you have credit card:"
        echo "  gcloud run deploy codingjudge-backend --source ./backend --platform managed --region us-central1 --allow-unauthenticated"
        echo "  gcloud run deploy codingjudge-frontend --source ./frontend --platform managed --region us-central1 --allow-unauthenticated"
        ;;
    railway)
        echo "🚂 Railway - REQUIRES CREDIT CARD"
        echo "================================="
        echo ""
        echo "⚠️  Railway REQUIRES credit card for verification!"
        echo "    If you don't have a credit card, use 'oracle' or 'koyeb' instead."
        ;;
    render)
        echo "🎨 Render Deployment (NO CREDIT CARD for free tier)"
        echo "==================================================="
        echo ""
        echo "✅ NO CREDIT CARD REQUIRED for free tier!"
        echo ""
        echo "Free tier limits:"
        echo "  - Web services: Sleep after 15 min inactivity"
        echo "  - PostgreSQL: Free for 90 days only"
        echo "  - 100 GB bandwidth/month"
        echo ""
        echo "Steps:"
        echo "1. Sign up at render.com (GitHub login, NO credit card)"
        echo "2. Create PostgreSQL database (free 90 days)"
        echo "3. Create Web Service for backend (Docker)"
        echo "4. Create Static Site for frontend"
        echo "5. Set environment variables in Render dashboard"
        echo ""
        echo "⚠️  Backend sleeps after 15 min inactivity (cold start ~30s)"
        echo "⚠️  PostgreSQL FREE for 90 days only, then paid"
        ;;
    koyeb)
        echo "🚀 Koyeb - NO CREDIT CARD (Free tier) + Supabase DB = 100% FREE!"
        echo "==============================================================="
        echo ""
        echo "✅ NO CREDIT CARD REQUIRED!"
        echo ""
        echo "Free tier:"
        echo "  - 1 free service (512MB RAM, 1 vCPU) - NO SLEEP!"
        echo "  - Custom domain + auto SSL"
        echo ""
        echo "Database: Use Supabase (FREE PostgreSQL forever)"
        echo "  1. Sign up at supabase.com (GitHub login, NO credit card)"
        echo "  2. Create project → Get connection string"
        echo ""
        echo "Deploy to Koyeb:"
        echo "1. Sign up at koyeb.com (GitHub login, NO credit card)"
        echo "2. Create App from GitHub repo"
        echo "2. Build: Dockerfile"
        echo "3. Set env vars (use Supabase connection string for DB)"
        echo "4. Deploy!"
        ;;
    koyeb-supabase)
        echo "🚀 Koyeb + Supabase = 100% FREE FOREVER (NO CREDIT CARD)"
        echo "========================================================"
        echo ""
        echo "✅ 100% FREE FOREVER - NO CREDIT CARD ANYWHERE!"
        echo ""
        echo "Step 1: Create FREE PostgreSQL at Supabase"
        echo "  1. Go to supabase.com → Sign up with GitHub (NO credit card)"
        echo "  2. Create new project → Wait 2 minutes"
        echo "  3. Settings → Database → Copy connection string"
        echo "     Format: postgresql://postgres:[PASSWORD]@db.[REF].supabase.co:5432/postgres"
        echo ""
        echo "Step 2: Deploy to Koyeb"
        echo "  1. Go to koyeb.com → Sign up with GitHub (NO credit card)"
        echo "  2. Create App → GitHub → Select repo: amansingh1207/Sitare_Coding_Platform"
        echo "  3. Builder: Dockerfile"
        echo "  4. Environment variables (in Koyeb dashboard):"
        echo "     DB_HOST=db.xxx.supabase.co"
        echo "     DB_PORT=5432"
        echo "     DB_NAME=postgres"
        echo "     DB_USER=postgres"
        echo "     DB_PASSWORD=your-supabase-password"
        echo "     MAIL_USERNAME=your@gmail.com"
        echo "     MAIL_PASSWORD=your-gmail-app-password"
        echo "     JWT_SECRET=your-256-bit-secret"
        echo "     DOMAIN=yourdomain.com"
        echo "  5. Deploy! Get URL like: https://your-app.koyeb.app"
        echo ""
        echo "Step 3: Custom Domain (optional)"
        echo "  Koyeb dashboard → Settings → Domains → Add yourdomain.com"
        echo "  Add CNAME record: yourdomain.com -> your-app.koyeb.app"
        echo ""
        echo "✅ 100% FREE FOREVER - No credit card, no sleep, free PostgreSQL!"
        ;;
    *)
        echo "Usage: ./one-click-deploy.sh [oracle|koyeb|koyeb-supabase|render|gcp|railway] [your-domain.com]"
        echo ""
        echo "🎯 TRULY FREE options (NO CREDIT CARD):"
        echo "  oracle          - Oracle Cloud Free Tier ⭐ BEST (always free, 4 ARM CPUs, 24GB RAM)"
        echo "  koyeb-supabase  - Koyeb + Supabase ⭐ 100% FREE FOREVER (no sleep, free PostgreSQL)"
        echo "  koyeb           - Koyeb only (needs external DB like Supabase)"
        echo "  render          - Render.com (sleeps after 15min, DB free 90 days only)"
        echo ""
        echo "❌ REQUIRE CREDIT CARD (avoid if no card):"
        echo "  gcp             - Google Cloud Run (requires billing)"
        echo "  railway         - Railway.app (requires verification)"
        echo "  fly             - Fly.io (requires verification)"
        echo ""
        echo "Example: ./one-click-deploy.sh oracle yourdomain.com"
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