# 新 Mac 开发环境配置清单（Java 全栈 + AWS）

按顺序装，整个过程大约 30–40 分钟。每一步后面都有验证命令。
Apple Silicon（M1/M2/M3/M4）和 Intel 都适用。

---

## 0. 基础：Xcode Command Line Tools + Homebrew

```bash
xcode-select --install          # 弹窗点"安装"，装 git / make / clang 等
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

装完 Homebrew 后它会提示你把两行 `eval "$(/opt/homebrew/bin/brew shellenv)"` 加到 `~/.zprofile`，照做。

```bash
brew --version
git --version
```

---

## 1. Java：用 SDKMAN 管理（推荐），不要直接 brew install java

SDKMAN 可以同时装多个 JDK 版本并随时切换，面试/工作中你迟早会遇到项目要 Java 17 而另一个要 21。

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

sdk list java | grep -E "21\.|17\." | head      # 看可用版本
sdk install java 21.0.5-tem                       # Eclipse Temurin 21（本项目用这个）
sdk install maven 3.9.9
```

验证：

```bash
java -version      # openjdk version "21.0.x"
mvn -version       # Apache Maven 3.9.x, Java version: 21
```

> Gradle 本项目不需要。以后遇到 Gradle 项目用 `sdk install gradle` 即可。

---

## 2. Node.js：用 nvm 管理

```bash
brew install nvm
mkdir -p ~/.nvm
```

把下面加到 `~/.zshrc`：

```bash
export NVM_DIR="$HOME/.nvm"
[ -s "/opt/homebrew/opt/nvm/nvm.sh" ] && \. "/opt/homebrew/opt/nvm/nvm.sh"
```

然后：

```bash
source ~/.zshrc
nvm install --lts          # 目前是 Node 22
nvm use --lts
node -v && npm -v
```

---

## 3. Docker Desktop

去 https://www.docker.com/products/docker-desktop/ 下载 **Apple Silicon** 或 Intel 版本安装。
或者：

```bash
brew install --cask docker
```

启动 Docker Desktop 一次（它需要你授权），然后：

```bash
docker --version
docker compose version     # 注意是 docker compose（v2），不是 docker-compose
docker run --rm hello-world
```

设置 → Resources 把内存调到至少 6 GB（Kafka + Postgres + 4 个 Spring Boot 服务一起跑需要）。

> 替代方案：如果 Docker Desktop 太重，可以用 OrbStack（`brew install --cask orbstack`），
> 更快更省电，兼容 docker 命令。二选一即可。

---

## 4. IDE

**后端：IntelliJ IDEA Community**（免费，学生可申请 Ultimate）

```bash
brew install --cask intellij-idea-ce
```

必装插件：Lombok（虽然本项目没用，以后会遇到）、Docker、Kubernetes。

**前端：VS Code**

```bash
brew install --cask visual-studio-code
```

插件：ESLint、Prettier、ES7+ React snippets、Docker、YAML。

---

## 5. 数据库 / 中间件客户端

```bash
brew install --cask dbeaver-community    # 通用数据库 GUI，连 Postgres/MySQL 都行
brew install postgresql@16               # 只为了有 psql 命令行工具，不需要启动服务
brew install redis                       # 同理，只为 redis-cli
brew install kcat                        # Kafka 命令行调试工具（原 kafkacat）
```

或者用 IntelliJ 自带的 Database 工具窗口。

---

## 6. API 调试

```bash
brew install --cask postman       # 或者 bruno（开源、本地存储）：brew install --cask bruno
brew install httpie               # 命令行：http GET localhost:8080/api/projects
brew install jq                   # JSON 格式化
```

---

## 7. AWS 工具链

```bash
brew install awscli
aws --version

aws configure                      # 输入 Access Key / Secret / region（建议 us-east-1）/ json
aws sts get-caller-identity        # 能返回你的 Account ID 就说明配置成功
```

> 安全提醒：在 AWS 控制台先开 **Billing Alert**（预算 $10/月告警），再创建一个 IAM 用户给 CLI 用，
> **不要用 root 账号的 Access Key**。

可选：

```bash
brew install terraform             # 如果你想用 IaC 管理 AWS 资源（本项目提供 Terraform 示例）
brew install kubectl               # 如果以后部署到 EKS
```

---

## 8. 其他好用的命令行工具

```bash
brew install gh          # GitHub CLI：gh repo create / gh pr create
brew install tree        # 看目录结构
brew install wget
brew install --cask iterm2    # 比自带 Terminal 好用
```

Git 全局配置：

```bash
git config --global user.name "你的名字"
git config --global user.email "你的 GitHub 邮箱"
git config --global init.defaultBranch main
ssh-keygen -t ed25519 -C "你的 GitHub 邮箱"      # 一路回车
cat ~/.ssh/id_ed25519.pub                       # 复制到 GitHub → Settings → SSH keys
```

---

## 9. 一键验证脚本

全部装完后运行项目根目录的 `scripts/check-env.sh`，它会逐项检查版本并告诉你缺什么。

---

## 常见坑

| 问题 | 解决 |
|---|---|
| `zsh: command not found: brew` | 没执行 `brew shellenv` 那两行，重开终端或 `source ~/.zprofile` |
| Maven 下载依赖很慢 | 一般不需要换源，美国网络直连 Maven Central 很快；如果在国内可配阿里云镜像 |
| Docker 启动报 "Cannot connect to the Docker daemon" | Docker Desktop 没启动，点开它等图标变绿 |
| IntelliJ 识别不到 JDK | File → Project Structure → SDK → Add JDK → 选 `~/.sdkman/candidates/java/current` |
| `npm install` 报 EACCES 权限错 | 说明没用 nvm 而是 brew 直接装的 node，卸掉改用 nvm |
| Apple Silicon 上某个 Docker 镜像拉不下来 | 加 `platform: linux/amd64` 到 compose 的那个 service（本项目的镜像都有 arm64 版本，不需要） |
