# CodingJudge Deployment Guide

## Overview
This guide covers deploying CodingJudge to a production server with automated CI/CD via GitHub Actions.

## Architecture
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Nginx     │────▶│  Frontend   │     │   Backend   │
│  (SSL/80/443)│     │  (React)    │     │  (Spring)   │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                                │
                    ┌─────────────┐     ┌───────┴───────┐
                    │  Postgres   │     │  Docker       │
                    │  (Data)     │     │  Sandbox      │
                    └─────────────┘     └───────────────┘
```

## Prerequisites
- Ubuntu 22.04/24.04 LTS server (2+ GB RAM, 2+ vCPU, 20+ GB disk)
- Domain name pointed to server IP
- Gmail account with App Password for SMTP
- GitHub repository with Actions enabled

---

## One-Time Server Setup

Run on your fresh server as root:

```bash
curl -fsSL https://raw.githubusercontent.com/amansingh1207/Sitare_Coding_Platform/main/server-setup.sh | sudo bash
```

This installs:
- Docker & Docker Compose
- Certbot (Let's Encrypt)
- Nginx (disabled, runs in Docker)
- UFW firewall (ports 22, 80, 443)
- systemd service for auto-start
- Log rotation for Docker

---

## Configuration

### 1. Create `.env.prod`

Copy `.env.prod.example` to `.env.prod` and fill in:

```bash
cp .env.prod.example .env.prod
nano .env.prod
```

**Required values:**
- `DB_PASSWORD` - Strong random password
- `JWT_SECRET` - 256+ bit random string
- `MAIL_USERNAME` / `MAIL_PASSWORD` - Gmail + App Password
- `DOMAIN` - Your production domain

### 2. DNS Configuration

Point your domain to server IP:
```
A     @          YOUR_SERVER_IP
A     www        YOUR_SERVER_IP
```

---

## Deployment Methods

### Method 1: Automated (GitHub Actions) - Recommended

**Setup once:**
1. Go to GitHub repo → Settings → Secrets and variables → Actions
2. Add repository secrets:
   - `SERVER_IP` - Your server IP
   - `SSH_USER` - `root` (or your user)
   - `SSH_PRIVATE_KEY` - Your SSH private key

**Deploy:** Push to `main` branch - GitHub Actions handles everything!

### Method 2: Manual Deploy Script

```bash
# From local machine
./deploy.sh YOUR_SERVER_IP root
```

---

## SSL Certificate Setup (First Time Only)

After first deployment:

```bash
ssh root@your-server
cd /opt/codingjudge

# Get initial certificate
docker compose -f docker-compose.prod.yml run --rm certbot \
  certonly --webroot -w /var/www/certbot -d your-domain.com -d www.your-domain.com

# Restart nginx to use certificates
docker compose -f docker-compose.prod.yml restart nginx
```

Certbot auto-renews every 12 hours via the certbot container.

---

## Common Operations

| Task | Command |
|------|---------|
| View logs | `docker compose -f docker-compose.prod.yml logs -f backend` |
| Restart backend | `docker compose -f docker-compose.prod.yml restart backend` |
| View all services | `docker compose -f docker-compose.prod.yml ps` |
| Backup database | `docker exec codingjudge-prod-postgres pg_dump -U codingjudge codingjudge > backup.sql` |
| Restore database | `cat backup.sql | docker exec -i codingjudge-prod-postgres psql -U codingjudge codingjudge` |
| Update app | Push to main (auto-deploy) or run `./deploy.sh` |
| Rollback | `docker compose -f docker-compose.prod.yml down && docker tag backend:prev backend:latest && docker compose up -d` |

---

## Monitoring & Health

- **Backend health**: `curl https://your-domain.com/api/health`
- **Database**: `docker exec codingjudge-prod-postgres pg_isready`
- **SSL expiry**: `docker exec codingjudge-certbot certbot certificates`
- **Disk space**: `df -h /opt/codingjudge`

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Backend won't start | Check `docker logs codingjudge-prod-backend` |
| DB connection failed | Verify `.env.prod` DB creds, check postgres container |
| Email not sending | Verify Gmail App Password, check backend logs |
| SSL cert failed | Ensure port 80 accessible, check certbot logs |
| Frontend 404 | Rebuild: `docker compose -f docker-compose.prod.yml build frontend` |

---

## Updating Application

**Automatic (GitHub Actions):**
```bash
git push origin main  # Triggers CI → Tests → Build → Deploy
```

**Manual:**
```bash
./deploy.sh your-server-ip root
```

---

## Security Checklist

- [ ] Strong `DB_PASSWORD` (32+ chars)
- [ ] Strong `JWT_SECRET` (64+ chars)
- [ ] Gmail App Password (not account password)
- [ ] UFW firewall enabled (22, 80, 443 only)
- [ ] SSH key auth only (disable password login)
- [ ] Regular `apt update && apt upgrade`
- [ ] Monitor `/var/log/auth.log` for failed SSH

---

## File Structure on Server

```
/opt/codingjudge/
├── docker-compose.prod.yml
├── .env.prod                 # Your secrets (NOT in git)
├── backend/
│   └── target/codingjudge-backend-0.1.0.jar
├── frontend/
│   └── dist/                 # Built React app
├── docker/
│   └── sandbox/Dockerfile
├── nginx/
│   ├── nginx.conf
│   └── conf.d/default.conf
├── certbot/
│   ├── conf/                 # Let's Encrypt certs
│   └── www/                  # ACME challenges
└── deploy.sh                 # Manual deploy script
```

---

## Rollback Procedure

```bash
# Quick rollback to previous version
ssh root@server
cd /opt/codingjudge
docker compose -f docker-compose.prod.yml down
docker tag codingjudge/backend:current codingjudge/backend:latest
docker compose -f docker-compose.prod.yml up -d
```

---

## Scaling Considerations

| Component | Current | Scale Up |
|-----------|---------|----------|
| Backend | 1 replica | Add replicas behind nginx |
| Postgres | Single | Read replicas, pgBouncer |
| Judge | 1 sandbox | Multiple sandbox containers |
| Frontend | Nginx static | CDN (Cloudflare) |

---

## Support

- Logs: `docker compose -f docker-compose.prod.yml logs -f [service]`
- GitHub Issues: https://github.com/amansingh1207/Sitare_Coding_Platform/issues
- Email: your-email@domain.com