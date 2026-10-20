# Rendered by terraform (infra/terraform/dev/main.tf) with cluster_name and
# one worker node per worker_count.
# Keep node/port layout in sync with platform/kind/cluster.yaml.
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: ${cluster_name}
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
                node-labels: "ingress-ready=true"
    # Ingress ports for istio-ingressgateway (host 80/443 → node 80/443).
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
%{ for worker in workers ~}
  - role: worker
%{ endfor ~}
