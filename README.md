# Taskyra: Discord Task Management Bot

Taskyra is a powerful, easy-to-deploy Discord bot for managing tasks within your server. Built with Kotlin,
Taskyra helps teams organize, assign, and track tasks directly from Discord using slash commands and interactive
buttons.

---

## Features

- **Task Management:** Create, assign, and update tasks with support for multiple states (Pending, In Progress,
  Completed).
- **Interactive Discord Commands:** Use slash commands to post tasks, configure channels, and update information.
- **Action Buttons:** Start, complete, delete, assign users, or edit tasks with a single click on Discord messages.
- **Channel Integration:** Automatically manages and summarizes tasks in designated Discord channels.
- **Easy Deployment:** Containerized with Docker and Docker Compose for quick setup.

---

## Getting Started

### Prerequisites

- [Docker](https://www.docker.com/get-started)
- [Docker Compose](https://docs.docker.com/compose/)
- A Discord bot token ([How to create a bot](https://discord.com/developers/applications))

### Installation

1. **Clone the repository:**
   ```sh
   git clone https://github.com/FrinshHD/Taskyra.git
   cd Taskyra
   ```

2. **Copy and configure environment variables:**
   ```sh
   cp .env.example .env
   # Edit .env and set your DISCORD_TOKEN and any other required variables
   ```

3. **Start Taskyra using Docker Compose:**
   ```sh
   docker-compose up -d --build
   ```

Taskyra will now be running in a container, using the configuration from your `.env` file.

---

## Usage

### Slash Commands

- `/posttask` — Create and post a new task.
- `/settaskchannels` — Configure which channels Taskyra uses.
- `/updateinfo` — Update bot or task information.