#!/bin/bash

echo "================================================"
echo "  OutOfMemoryError 测试环境启动脚本"
echo "================================================"
echo ""
echo "此脚本将以限制内存模式启动应用"
echo "JVM 参数：-Xmx256m -Xms128m"
echo ""
echo "⚠️ 警告：此模式下运行 OOM 测试会导致应用崩溃！"
echo ""
echo "启动中..."
echo ""

mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx256m -Xms128m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heap_dump.hprof"

