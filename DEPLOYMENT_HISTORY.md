# Free Deployment History - CodingJudge

## Deployment Log

| Date | Version | Provider | Changes | Status |
|------|---------|----------|---------|--------|
| $(date '+%Y-%m-%d') | 0.1.0 | - | Initial deployment preparation | Ready |

## Deployment Commands Used

### Oracle Cloud Free Tier (RECOMMENDED - Always Free)
```bash
# One-time server setup
curl -fsSL https://raw.githubusercontent.com/amansingh1207/Sitare_Coding_Platform/main/server-setup.sh | sudo bash

# Configure
cd /opt/codingjudge
cp .env.prod.example .env.prod
nano .env.prod

# Deploy
docker compose -f docker-compose.prod.yml up -d

# SSL Certificate
docker compose -f docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot -d yourdomain.com
docker compose -f docker-compose.prod.yml restart nginx
```

### Google Cloud Run (Free Tier: 2M req/mo)
```bash
gcloud run deploy codingjudge-backend --source ./backend --platform managed --region us-central1 --allow-unauthenticated
gcloud run deploy codingjudge-frontend --source ./frontend --platform managed --region us-central1 --allow-unauthenticated
```

### Railway (Free $5/mo credit)
```bash
# Connect GitHub at railway.app
# Add PostgreSQL plugin
# Set environment variables
# Deploy!
```

### Render (Free, sleeps after 15min)
```bash
# Connect GitHub at render.com
# Add PostgreSQL (free 90 days)
# Create Web Service (backend)
# Create Static Site (frontend)
```

## Free Provider Comparison

| Provider | Compute | Database | SSL | Custom Domain | Sleep | Best For |
|----------|---------|----------|-----|---------------|-------|----------|
| **Oracle Cloud** | 4 ARM CPU, 24GB RAM (Always Free) | Self-hosted Postgres | Let's Encrypt | ✅ | Never | **Production** |
| **Google Cloud Run** | 2M req/mo free | Cloud SQL (paid) | Auto | ✅ | Never | Low traffic |
| **Railway** | $5/mo credit | Included | Auto | ✅ | Never | Hobby |
| **Render** | Sleeps after 15min | Free 90 days | Auto | ✅ | After 15min | Demos |

## Environment Variables Checklist

- [ ] `DB_PASSWORD` - Strong random password (32+ chars)
- [ ] `JWT_SECRET` - 256+ bit random string
- [ ] `MAIL_USERNAME` - Gmail address
- [ ] `MAIL_PASSWORD` - Gmail App Password (NOT account password)
- [ ] `MAIL_FROM_NAME` - "CodingJudge"
- [ ] `DOMAIN` - yourdomain.com
- [ ] `JUDGE_DOCKER_IMAGE` - codingjudge/sandbox:latest
- [ ] `SPRING_PROFILES_ACTIVE=prod`

## Quick Deploy Commands

```bash
# Option 1: GitHub Actions (Auto-deploy on push)
git push origin main

# Option 2: Manual deploy script
./deploy.sh your-server-ip root

# Option 3: Free cloud specific
./deploy-free.sh oracle    # Oracle Cloud (Best free)
./deploy-free.sh gcp       # Google Cloud Run
./deploy-free.sh railway   # Railway
./deploy-free.sh render    # Render
```

## Troubleshooting Quick Reference

| Issue | Solution |
|-------|----------|
| Backend won't start | `docker logs codingjudge-prod-backend` |
| DB connection failed | Check `.env.prod` DB creds, verify postgres running |
| Email not sending | Verify Gmail App Password, check backend logs |
| SSL cert failed | Ensure port 80 open, check certbot logs |
| Frontend 404 | Rebuild: `docker compose build frontend` |
| Out of memory | Increase swap or reduce JVM heap |

## Rollback Procedure

```bash
# Quick rollback
docker compose -f docker-compose.prod.yml down
docker tag codingjudge/backend:prev codingjudge/backend:latest
docker compose -f docker-compose.prod.yml up -d
```

## Monitoring

```bash
# Health check
curl https://yourdomain.com/api/health

# Database
docker exec codingjudge-prod-postgres pg_isready

# SSL expiry
docker exec codingjudge-certbot certbot certificates

# Disk space
df -h /opt/codingjudge
```

## Next Deployment Checklist

- [ ] Pull latest code: `git pull`
- [ ] Run tests: `cd backend && mvn test` + `cd frontend && npm test`
- [ ] Build: `cd backend && mvn package -DskipTests` + `cd frontend && npm run build`
- [ ] Deploy: `./deploy.sh` or `git push origin main`
- [ ] Verify: `curl https://yourdomain.com/api/health`
- [ ] Check logs: `docker compose -f docker-compose.prod.yml logs -f backend`