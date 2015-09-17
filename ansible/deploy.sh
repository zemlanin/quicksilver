#!/bin/bash
# lein uberjar
mkdir -p ansible/infrastructure
cp target/uberjar/quicksilver-0.1.0-SNAPSHOT-standalone.jar ansible/infrastructure/qs.jar
ansible-playbook ansible/deploy.yaml
