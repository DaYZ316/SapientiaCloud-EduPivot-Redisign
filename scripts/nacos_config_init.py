#!/usr/bin/env python3
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


GROUPS = {
    "root": "EDUPIVOT_NAVIGATOR",
    "docker": "EDUPIVOT_DOCKER",
    "local": "EDUPIVOT_LOCAL",
}

APP_NAMES = {
    "sc-edupivot-auth-common.yaml": "sc-auth",
    "sc-edupivot-auth-infra-docker.yaml": "sc-auth",
    "sc-edupivot-auth-infra-local.yaml": "sc-auth",
    "sc-edupivot-course-common.yaml": "sc-course",
    "sc-edupivot-course-infra-docker.yaml": "sc-course",
    "sc-edupivot-course-infra-local.yaml": "sc-course",
    "sc-edupivot-gateway-common.yaml": "sc-gateway",
    "sc-edupivot-gateway-common-docker.yaml": "sc-gateway",
    "sc-edupivot-gateway-routes-docker.yaml": "sc-gateway",
    "sc-edupivot-gateway-routes-local.yaml": "sc-gateway",
    "sc-edupivot-notification-common.yaml": "sc-notification",
    "sc-edupivot-notification-infra-docker.yaml": "sc-notification",
    "sc-edupivot-notification-infra-local.yaml": "sc-notification",
    "sc-edupivot-storage-common.yaml": "sc-storage",
    "sc-edupivot-storage-infra-docker.yaml": "sc-storage",
    "sc-edupivot-storage-infra-local.yaml": "sc-storage",
    "sc-edupivot-ai-common.yaml": "sc-ai",
    "sc-edupivot-ai-infra-docker.yaml": "sc-ai",
    "sc-edupivot-ai-infra-local.yaml": "sc-ai",
    "sc-edupivot-infra-docker.yaml": "sc-edupivot",
    "sc-edupivot-infra-local.yaml": "sc-edupivot",
}


def server_url() -> str:
    addr = os.environ.get("NACOS_SERVER_ADDR", "nacos:8848").strip()
    if not addr.startswith(("http://", "https://")):
        addr = f"http://{addr}"
    return addr.rstrip("/")


def post_form(url: str, data: dict[str, str]) -> str:
    encoded = urllib.parse.urlencode(data).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=encoded,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.read().decode("utf-8")


def login(base_url: str) -> str:
    username = os.environ.get("NACOS_USERNAME", "nacos")
    password = os.environ.get("NACOS_PASSWORD", "nacos")
    login_url = f"{base_url}/nacos/v1/auth/users/login"
    for attempt in range(1, 31):
        try:
            response = post_form(login_url, {"username": username, "password": password})
            token = json.loads(response).get("accessToken")
            if token:
                return token
            raise RuntimeError(f"Nacos login response did not include accessToken: {response}")
        except Exception as exc:
            if attempt == 30:
                raise
            print(f"Waiting for Nacos auth ({attempt}/30): {exc}", flush=True)
            time.sleep(2)
    raise RuntimeError("Nacos login failed")


def config_files(config_dir: Path) -> list[tuple[str, str, Path]]:
    files: list[tuple[str, str, Path]] = []
    files.extend((GROUPS["root"], path.name, path) for path in sorted(config_dir.glob("*.yaml")))
    for folder_name in ("docker", "local"):
        folder = config_dir / folder_name
        if folder.exists():
            files.extend((GROUPS[folder_name], path.name, path) for path in sorted(folder.glob("*.yaml")))
    return files


def substitute_env_vars(content: str) -> str:
    """将 ${VAR} 和 ${VAR:default} 替换为环境变量值。"""
    def replacer(match: re.Match) -> str:
        var_expr = match.group(1)
        if ":" in var_expr:
            var_name, default = var_expr.split(":", 1)
        else:
            var_name, default = var_expr, ""
        return os.environ.get(var_name.strip(), default)
    return re.sub(r"\$\{([^}]+)}", replacer, content)


def publish_config(base_url: str, token: str, group: str, data_id: str, path: Path) -> None:
    content = substitute_env_vars(path.read_text(encoding="utf-8"))
    app_name = APP_NAMES.get(data_id, "sc-edupivot")
    response = post_form(
        f"{base_url}/nacos/v1/cs/configs",
        {
            "dataId": data_id,
            "group": group,
            "appName": app_name,
            "type": "yaml",
            "content": content,
            "accessToken": token,
        },
    )
    if response.strip().lower() != "true":
        raise RuntimeError(f"Failed to publish {group}/{data_id}: {response}")
    print(f"Published {group}/{data_id} ({app_name})", flush=True)


def main() -> int:
    config_dir = Path(os.environ.get("NACOS_CONFIG_DIR", "/nacos-config"))
    if not config_dir.exists():
        print(f"Nacos config directory does not exist: {config_dir}", file=sys.stderr)
        return 1

    base_url = server_url()
    token = login(base_url)
    files = config_files(config_dir)
    if not files:
        print(f"No Nacos YAML config files found under {config_dir}", file=sys.stderr)
        return 1

    for group, data_id, path in files:
        try:
            publish_config(base_url, token, group, data_id, path)
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code} while publishing {group}/{data_id}: {detail}") from exc
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
