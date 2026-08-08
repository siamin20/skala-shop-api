# 쿠버네티스 배포

## 적용

```bash
# 이미지를 먼저 만든다
docker build -t shop-api:1.0 .

# 로컬 클러스터라면 노드에 이미지를 올린다 (레지스트리를 쓰지 않는 경우)
#   kind:     kind load docker-image shop-api:1.0
#   minikube: minikube image load shop-api:1.0
#   Docker Desktop: 별도 작업 불필요 (같은 데몬을 공유한다)

kubectl apply -f infra/k8s/

kubectl -n skala-shop rollout status deployment/shop-api
kubectl -n skala-shop port-forward svc/shop-api 8080:80
curl http://localhost:8080/actuator/health
```

## 파일 순서

번호는 적용 순서다. `kubectl apply -f 디렉터리`는 파일 이름순으로 적용한다.
네임스페이스가 없으면 나머지가 전부 실패하므로 `00-`으로 시작한다.

| 파일 | 내용 |
| --- | --- |
| `00-namespace.yaml` | 네임스페이스 |
| `01-config.yaml` | ConfigMap과 Secret |
| `02-postgres.yaml` | PostgreSQL (StatefulSet) |
| `03-redis.yaml` | Redis (Deployment) |
| `04-app.yaml` | 애플리케이션 (Deployment + Service) |

## 검증 상태

**매니페스트 문법은 `kubectl apply --dry-run=client`로 검증했다.**
`kubectl --dry-run=server`와 실제 클러스터 기동은 **확인하지 않았다.**
이 환경에 클러스터가 없기 때문이다(kind·minikube·Docker Desktop 쿠버네티스 모두 없음).

따라서 아래는 검증되지 않았다.

- 파드가 실제로 Ready가 되는가
- probe 임계값(startup 60초 등)이 실제 기동 시간에 맞는가
- PVC가 바인딩되는가

클러스터가 준비되면 위 적용 절차로 확인한 뒤 이 문단을 측정 결과로 교체한다.

## 이 구성에서 다루지 않은 것

| 항목 | 왜 없는가 |
| --- | --- |
| Ingress | 클러스터마다 컨트롤러가 달라 그대로 쓸 수 없다. port-forward로 대신한다 |
| HPA | 부하 기준을 측정하지 않았다. 근거 없는 임계값을 넣지 않는다 |
| NetworkPolicy | CNI가 지원해야 동작한다. 지원하지 않는 클러스터에서는 조용히 무시된다 |
| PodDisruptionBudget | 노드가 하나뿐인 로컬 클러스터에서는 오히려 드레인을 막는다 |
| Secret 외부화 | External Secrets나 Sealed Secrets가 맞지만 클러스터에 설치가 필요하다 |
