#!/bin/bash

# ShareNote Data Redistribution - Test Data Generation Script
# Generates test data in the legacy PostgreSQL database

set -e

# Configuration
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="sharenote_legacy"
DB_USER="sharenote_user"
DB_PASSWORD="sharenote_password"

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/generate-test-data.sql"

echo "🚀 Starting test data generation for ShareNote Data Redistribution..."
echo ""

# Check if Docker containers are running
echo "🔍 Checking if PostgreSQL containers are running..."
if ! docker ps | grep -q "sharenote-postgres-legacy"; then
    echo "❌ Error: PostgreSQL legacy container is not running!"
    echo "   Please run: cd ../docker && ./start.sh"
    exit 1
fi

if ! docker ps | grep -q "Up.*healthy.*5432"; then
    echo "❌ Error: PostgreSQL legacy container is not healthy!"
    echo "   Please wait for the container to be fully ready"
    exit 1
fi

echo "✅ PostgreSQL containers are running"
echo ""

# Test database connection
echo "🔗 Testing database connection..."
if ! docker exec sharenote-postgres-legacy psql -U $DB_USER -d $DB_NAME -c "SELECT 1;" >/dev/null 2>&1; then
    echo "❌ Error: Cannot connect to database!"
    echo "   Please check if the PostgreSQL container is running and healthy"
    exit 1
fi

echo "✅ Database connection successful"
echo ""

# Check if SQL file exists
if [ ! -f "$SQL_FILE" ]; then
    echo "❌ Error: SQL file not found: $SQL_FILE"
    exit 1
fi

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

# Confirm execution
echo "⚠️  This will generate a large amount of test data:"
echo "   - 50,000 pages"
echo "   - 150,000 page permissions (3 per page)"
echo "   - 1,500,000 blocks (30 per page)"
echo ""
echo "   Estimated time: 5-10 minutes"
echo "   Estimated disk space: ~500MB"
echo ""

read -p "Do you want to continue? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "🚫 Operation cancelled"
    exit 0
fi

echo ""
echo "⏱️  Starting data generation... (this may take several minutes)"
echo ""

# Record start time
START_TIME=$(date +%s)

# Execute SQL script with progress output
docker exec -i sharenote-postgres-legacy psql -U $DB_USER -d $DB_NAME < "$SQL_FILE"

# Record end time
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "✅ Test data generation completed!"
echo "⏱️  Total time: ${DURATION} seconds"
echo ""

# Show final data counts
echo "📊 Final data counts:"
docker exec sharenote-postgres-legacy psql -U $DB_USER -d $DB_NAME -c "
SELECT 'Pages' as entity, count(*) as total_count FROM pages
UNION ALL
SELECT 'Page Permissions' as entity, count(*) as total_count FROM page_permissions
UNION ALL
SELECT 'Blocks' as entity, count(*) as total_count FROM blocks;
"
echo ""

# Show sample data
echo "📋 Sample data preview:"
docker exec sharenote-postgres-legacy psql -U $DB_USER -d $DB_NAME -c "
SELECT
    'Page Sample' as type,
    p.title,
    p.migration_status,
    p.created_at::date as created_date
FROM pages p
ORDER BY p.created_at DESC
LIMIT 3;
"
echo ""

echo "🎉 Test data generation successful!"
echo ""
echo "📝 Next steps:"
echo "   1. You can now test the data redistribution application"
echo "   2. All data is marked with migration_status = 'READY'"
echo "   3. Use the clean-data.sh script to remove test data if needed"