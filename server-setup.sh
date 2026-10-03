#!/bin/bash
# server-setup.sh - Run ONCE on a fresh Ubuntu 22.04/24.04 server
# Usage: curl -fsSL https://raw.githubusercontent.com/your-repo/main/server-setup.sh | sudo bash

set -e

echo "🔧 Setting up CodingJudge production server..."

# Update system
apt-get update && apt-get upgrade -y

# Install Docker
if ! command -v docker &> /dev/null; then
    echo "📦 Installing Docker..."
    curl -fsSL https://get.docker.com | sh
    usermod -aG docker $SUDO_USER
fi

# Install Docker Compose plugin
if ! docker compose version &> /dev/null; then
    echo "📦 Installing Docker Compose..."
    apt-get install -y docker-compose-plugin
fi

# Install Certbot
if ! command -v certbot &> /dev/null; then
    echo "🔐 Installing Certbot..."
    apt-get install -y certbot
fi

# Install Nginx (for initial cert generation before docker)
if ! command -v nginx &> /dev/null; then
    echo "🌐 Installing Nginx..."
    apt-get install -y nginx
    systemctl stop nginx
    systemctl disable nginx
fi

# Create project directory
mkdir -p /opt/codingjudge
cd /opt/codingjudge

# Setup firewall
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# Setup log rotation for Docker
cat > /etc/logrotate.d/docker << 'EOF'
/var/lib/docker/containers/*/*.log {
    rotate 7
    daily
    compress
    size=10M
    missingok
    delaycompress
    copytruncate
}
EOF

# Create systemd service for auto-start
cat > /etc/systemd/system/codingjudge.service << 'EOF'
[Unit]
Description=CodingJudge Application
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/codingjudge
ExecStart=/usr/bin/docker compose -f docker-compose.prod.yml up -d
ExecStop=/usr/bin/docker compose -f docker-compose.prod.yml down
TimeoutStartSec=120

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable codingjudge.service

echo "✅ Server setup complete!"
echo ""
echo "📝 Next steps:"
echo "1. cd /opt/codingjudge"
echo "2. Copy your .env.prod file here"
echo "3. Run: docker compose -f docker-compose.prod.yml up -d"
echo "4. Get SSL cert: docker compose -f docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot -d your-domain.com"
echo "5. Restart nginx: docker compose -f docker-compose.prod.yml restart nginx"
echo "6. Enable auto-start: systemctl enable codingjudge.service"