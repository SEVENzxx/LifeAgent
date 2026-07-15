import logging

from fastapi import FastAPI

from lifeagent_ai.api.routes import router
from lifeagent_ai.config import APP_DESCRIPTION, APP_TITLE, APP_VERSION

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-5s %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

app = FastAPI(title=APP_TITLE, description=APP_DESCRIPTION, version=APP_VERSION)
app.include_router(router)
