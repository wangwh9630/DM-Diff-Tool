#!/bin/bash

export JAVA_HOME=./jre21
export PATH=$JAVA_HOME/bin:$PATH

echo "正在启动 DM-Diff 工具..."
java -jar dm-diff-tool.jar