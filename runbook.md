## CLI commands for endpoints

### Greetings

```bash
http POST http://localhost:9000/greetings/123 message=hi
```


### Activity agent
```bash
curl -i -XPOST --location "http://localhost:9000/activities" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do?"}'
```

### Worlflow

```bash
curl -i localhost:9000/preferences/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{
    "preference": "I dislike museums."
  }'

curl -i localhost:9000/preferences/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{
    "preference": "I like outdoor activities."
  }'

curl -i localhost:9000/preferences/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{
    "preference": "I like indian and italian food."
  }'


curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do?"}'


http get http://localhost:9000/activities/alice/workflow/{taskId}

```

### Autonomous
```bash
 curl -i -XPOST --location "http://localhost:9000/activities/autonomous/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do? Beware of the weather."}'

```
