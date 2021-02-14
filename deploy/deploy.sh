#!/usr/bin/env bash


function build_container() {
  APP_NAME=bk
  GCR_IMAGE_NAME=gcr.io/bootiful/${APP_NAME}
  mvn -f pom.xml \
    -DskipTests=true \
    clean spring-boot:build-image -e \
    -Dspring.profiles.active=production,cloud \
    -Dspring-boot.build-image.imageName=$GCR_IMAGE_NAME \
    clean spring-boot:build-image -e
  IMAGE_ID="$(docker images -q $GCR_IMAGE_NAME)"
  docker tag "${IMAGE_ID}" ${GCR_IMAGE_NAME}:latest
  docker push ${GCR_IMAGE_NAME}:latest
}


echo "going to deploy the application"
NS=bk
kubectl get ns/$NS || kubectl create namespace $NS
cd $( dirname $0 )/..
build_container
cd $( dirname $0 )
kubectl apply -f bk.yaml -n $NS
