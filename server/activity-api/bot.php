<?php
declare(strict_types=1);

// Acknowledge callbacks from installations where the old webhook is still configured.
http_response_code(204);
