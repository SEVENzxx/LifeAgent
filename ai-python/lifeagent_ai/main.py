from fastapi import FastAPI

from lifeagent_ai.api import router
from lifeagent_ai.config import APP_DESCRIPTION, APP_TITLE, APP_VERSION

app = FastAPI(title=APP_TITLE, description=APP_DESCRIPTION, version=APP_VERSION)
app.include_router(router)
