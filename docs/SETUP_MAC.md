# Dev Environment Setup for a New Mac (Java full-stack + AWS)

Follow in order; the whole thing takes 30–40 minutes. Each step ends with a verification command.
Works on Apple Silicon (M1–M4) and Intel.

[中文版](SETUP_MAC.zh-CN.md)

> Everything in a code block is typed into **Terminal** (⌘ + Space → "Terminal", or Finder → Applications → Utilities).
> Paste one command at a time and wait for it to finish. Don't paste the `#` comments.

---

## 0. Basics: Xcode Command Line Tools + Homebrew

```bash
xcode-select --install
```

Click "Install" in the dialog. Then:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

It asks for your Mac login password (nothing is echoed while typing — that's normal). Newer installers add `brew` to your PATH automatically; if the final "Next steps" section shows two `eval "$(/opt/homebrew/bin/brew shellenv)"` lines, run them.

```bash
brew --version
git --version
```

---

## 1. Java: manage with SDKMAN (don't `brew install java` directly)

SDKMAN lets you install several JDKs side by side and switch — you *will* hit a project that needs 17 while another needs 21.

SDKMAN requires Bash 4+, and macOS ships Bash 3.2, so first:

```bash
brew install bash
curl -s "https://get.sdkman.io" | /opt/homebrew/bin/bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

```bash
sdk install java 21.0.5-tem     # Eclipse Temurin 21 (this project uses it); answer Y to "set as default"
sdk install maven
```

Verify:

```bash
java -version      # openjdk version "21.0.x"
mvn -version       # Apache Maven 3.9+, Java version: 21
```

> Gradle isn't needed for this project; `sdk install gradle` when you meet one.

---

## 2. Node.js: manage with nvm

```bash
brew install nvm
mkdir -p ~/.nvm
```

Append to `~/.zshrc` (paste all three lines together):

```bash
cat >> ~/.zshrc <<'EOF'
export NVM_DIR="$HOME/.nvm"
[ -s "/opt/homebrew/opt/nvm/nvm.sh" ] && \. "/opt/homebrew/opt/nvm/nvm.sh"
EOF
```

```bash
source ~/.zshrc
nvm install --lts
node -v && npm -v
```

---

## 3. Docker Desktop

```bash
brew install --cask docker
```

**Don't open Docker until the install finishes** (`🍺 docker-desktop was successfully installed!`) — opening it mid-install makes Homebrew's final step fail and roll back. Then:

```bash
open -a Docker
```

First launch: authorize with your password, accept the terms, **skip** sign-in. If it reports "Rosetta installation failed", click **Disable Rosetta** — every image in this project is native arm64.

Settings → **VM resources** → Memory ≥ **6 GB** (8 GB recommended; Kafka + Postgres + 4 JVMs).

```bash
docker --version
docker compose version        # note: "docker compose" (v2), not "docker-compose"
docker run --rm hello-world
```

> Lighter alternative: OrbStack (`brew install --cask orbstack`) — faster, less battery, same `docker` CLI. Pick one.

---

## 4. IDEs

**Backend: IntelliJ IDEA Community** (free; students can get Ultimate)

```bash
brew install --cask intellij-idea-ce
```

Plugins: Lombok (not used here but you'll meet it), Docker, Kubernetes.
If IntelliJ can't find the JDK: File → Project Structure → SDK → Add JDK → `~/.sdkman/candidates/java/current`.

**Frontend: VS Code**

```bash
brew install --cask visual-studio-code
```

Extensions: ESLint, Prettier, ES7+ React snippets, Docker, YAML.

---

## 5. Database / middleware clients

```bash
brew install --cask dbeaver-community    # universal DB GUI (Postgres, MySQL, …)
brew install postgresql@16               # just for the psql CLI; no need to start the service
brew install redis                       # just for redis-cli
brew install kcat                        # Kafka CLI debugging (formerly kafkacat)
```

Or use IntelliJ's built-in Database tool window.

---

## 6. API tooling

```bash
brew install --cask postman       # or Bruno (open source, local-first): brew install --cask bruno
brew install httpie               # CLI: http GET localhost:8080/api/projects
brew install jq                   # JSON pretty-printing
```

---

## 7. AWS toolchain

```bash
brew install awscli
aws --version

aws configure                      # Access Key / Secret / region (us-east-1 suggested) / json
aws sts get-caller-identity        # returns your Account ID when configured correctly
```

> Security: set a **Billing Alert** first (budget of $10/month with email alerts), then create an IAM user for the CLI.
> **Never use the root account's access key.**

Optional:

```bash
brew install terraform             # IaC for AWS (this repo ships Terraform)
brew install kubectl               # if you deploy to EKS later
```

---

## 8. Other handy CLI tools

```bash
brew install gh          # GitHub CLI: gh auth login / gh repo create / gh pr create
brew install tree        # directory trees
brew install wget
brew install --cask iterm2    # nicer than the stock Terminal
```

Git identity (use the email tied to your GitHub account, or your GitHub noreply address, so commits count toward your profile):

```bash
git config --global user.name "Your Name"
git config --global user.email "your-github-email"
git config --global init.defaultBranch main
ssh-keygen -t ed25519 -C "your-github-email"      # press Enter through the prompts
cat ~/.ssh/id_ed25519.pub                         # paste into GitHub → Settings → SSH keys
```

GitHub no longer accepts passwords for `git push`; run `gh auth login` once (browser flow) and it configures Git credentials for you.

---

## 9. One-shot verification

From the project root:

```bash
scripts/check-env.sh
```

It checks every tool and version and tells you what's missing.

---

## Common pitfalls

| Problem | Fix |
|---|---|
| `zsh: command not found: brew` | The `brew shellenv` lines weren't run; reopen Terminal or `source ~/.zprofile` |
| SDKMAN: "requires Bash 4 or higher" | `brew install bash`, then re-run the installer with `/opt/homebrew/bin/bash` (step 1) |
| Spotlight can't find Terminal on a brand-new Mac | Index not built yet; Finder → Applications → Utilities → Terminal |
| macOS 26 (Tahoe) has no Launchpad | Use Spotlight, Finder → Applications, or `open -a Docker` |
| Docker: "Cannot connect to the Docker daemon" | Docker Desktop isn't running; open it and wait for the whale icon to settle |
| `brew install --cask docker` fails with `xattr: Operation not permitted` | Docker.app was opened before the install finished; quit Docker and re-run the install |
| Maven downloads are slow | Usually fine from the US; in China configure the Aliyun mirror |
| `npm install` → EACCES permission error | Node was installed via brew instead of nvm; uninstall and use nvm |
| A Docker image has no arm64 build | Add `platform: linux/amd64` to that service in compose (not needed for this project) |
| `[Fatal Error] DOCTYPE is disallowed` during `mvn test` | Expected output from the XXE-protection test — not a failure |
