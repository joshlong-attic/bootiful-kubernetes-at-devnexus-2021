#!/usr/bin/env bash

echo "going to deploy the application"
NS=bk
kubectl get ns/$NS || kubectl create namespace $NS
kubectl apply -f bk.yaml -n $NS
