# Trip planner

a telegram bot that suppose to help you share your trips with friends and find new routes

## Running in repl

Make sure that `TELEGRAM_TOKEN` variable is set

- `lein repl` to start a new repl
- run `trip-planner.dev/-main`

## Running in a container on a remote VPS

on the server run:
- `sudo docker run -v db:/usr/src/app/db -e TELEGRAM_TOKEN=<TOKEN> -p 54320:54320 -it trip-planner lein repl :start :host 0.0.0.0 :port 54320`

on your local machine:
- `ssh -R 54320:localhost:54320 <remote-host>`

nrepl on port 54320 should now be available on your local machine
