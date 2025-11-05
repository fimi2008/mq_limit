#!/bin/bash

echo "================================================"
echo "  OutOfMemoryError 快速测试脚本"
echo "================================================"
echo ""
echo "此脚本将自动执行以下操作："
echo "1. 启动内存泄漏模式"
echo "2. 循环发送测试消息"
echo "3. 实时监控内存状态"
echo "4. 等待 OOM 发生"
echo ""
echo "⚠️ 确保应用已启动！(http://localhost:8080)"
echo ""
read -p "按回车键继续..."

BASE_URL="http://localhost:8080"

echo ""
echo "[步骤 1/3] 启动内存泄漏模式..."
curl -X POST "${BASE_URL}/oom/start?sizeKB=100"
echo ""
echo ""

echo "[步骤 2/3] 查看初始内存状态..."
curl -X GET "${BASE_URL}/oom/stats"
echo ""
echo ""

echo "[步骤 3/3] 开始循环发送消息并监控内存..."
echo "按 Ctrl+C 可随时停止"
echo ""

while true; do
    echo "----------------------------------------"
    echo "发送批量消息 (100条)..."
    curl -X POST "${BASE_URL}/oom/send-batch?count=100"
    echo ""
    echo ""
    
    echo "等待3秒后查看内存状态..."
    sleep 3
    echo ""
    
    echo "当前内存状态："
    curl -X GET "${BASE_URL}/oom/stats"
    echo ""
    echo ""
    
    echo "等待2秒后继续..."
    sleep 2
done

