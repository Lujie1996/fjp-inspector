#!/bin/bash
set -e

mkdir -p out
javac -d out ForkJoinPoolAgent.java

jar cfm fjp-agent.jar MANIFEST.MF -C out .
echo "Built fjp-agent.jar"

