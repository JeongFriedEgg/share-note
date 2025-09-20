#!/bin/bash

# ShareNote Data Redistribution - Test Data Cleanup Script
# Removes all test data from the legacy PostgreSQL database

set -e

# Configuration
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="sharenote_legacy"
DB_USER="sharenote_user"
DB_PASSWORD="sharenote_password"

echo "🧹 ShareNote Test Data Cleanup Script"
echo ""

# Check if Docker containers are running
echo "🔍 Checking if PostgreSQL containers are running..."
if ! docker ps | grep -q "sharenote-postgres-legacy"; then
    echo "❌ Error: PostgreSQL legacy container is not running!"
    exit 1
fi

echo "✅ PostgreSQL container is running"
echo ""

# Test database connection
echo "🔗 Testing database connection..."
if ! docker exec sharenote-postgres-legacy psql -U $DB_USER -d $DB_NAME -c "SELECT 1;" >/dev/null 2>&1; then
    echo "❌ Error: Cannot connect to database!"
    exit 1
fi

echo "✅ Database connection successful"
echo ""

# Show current data counts
echo "📊 Current data counts:"
docker exec sharenote-postgres-legacy psql -U $DB_USER -d $DB_NAME -c "
SELECT 'Pages' as entity, count(*) as current_count FROM pages
UNION ALL
SELECT 'Page Permissions' as entity, count(*) as current_count FROM page_permissions
UNION ALL
SELECT 'Blocks' as entity, count(*) as current_count FROM blocks;
"
echo ""

# Confirm deletion
echo "⚠️  This will DELETE ALL data from the following tables:"
echo "   - pages"
echo "   - page_permissions"
echo "   - blocks"
echo ""

read -p "Are you sure you want to continue? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "🚫 Operation cancelled"
    exit 0
fi

echo ""
echo "🗑️  Cleaning up test data..."

# Clean up data (order matters due to foreign key constraints)
docker exec sharenote-postgres-legacy psql -U $DB_USER -d $DB_NAME -c "
BEGIN;

-- Delete in correct order to avoid foreign key violations
DELETE FROM blocks;
DELETE FROM page_permissions;
DELETE FROM pages;

-- Reset sequences if they exist
-- (PostgreSQL doesn't have AUTO_INCREMENT like MySQL, but we can reset sequences)

COMMIT;
"

echo ""
echo "✅ Test data cleanup completed!"
echo ""

# Show final data counts
echo "📊 Final data counts:"
docker exec sharenote-postgres-legacy psql -U $DB_USER -d $DB_NAME -c "
SELECT 'Pages' as entity, count(*) as final_count FROM pages
UNION ALL
SELECT 'Page Permissions' as entity, count(*) as final_count FROM page_permissions
UNION ALL
SELECT 'Blocks' as entity, count(*) as final_count FROM blocks;
"
echo ""

echo "🎉 Database cleanup successful!"