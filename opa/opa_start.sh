# docker run -v policy:/policy -p 8181:8181 openpolicyagent/opa:0.49.0-static run --server --log-level debug /policy
opa run --server --log-level debug policy
