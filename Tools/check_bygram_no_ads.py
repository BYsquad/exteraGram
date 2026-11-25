"""Check all native Telegram ad entry points, including cached video ads."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java/org/telegram"
messages = (ROOT / "messenger/MessagesController.java").read_text(encoding="utf-8")
disabled = messages.split("public boolean isSponsoredDisabled() {", 1)[1].split("}", 1)[0]
assert disabled.strip() == "return true;", "Ads must be disabled for every account"
load = messages.split("public SponsoredMessagesInfo getSponsoredMessages(long dialogId) {", 1)[1]
assert load.index("if (isSponsoredDisabled()) return null;") < load.index("sponsoredMessages.get(dialogId)"), "Channel/bot ads must not load from cache or network"
for file in ("ui/ChatActivity.java", "ui/Adapters/DialogsSearchAdapter.java", "messenger/video/VideoAds.java"):
    text = (ROOT / file).read_text(encoding="utf-8")
    assert "isSponsoredDisabled()" in text, f"Missing ad guard: {file}"
    assert ".isPremium() && " not in "\n".join(line for line in text.splitlines() if "isSponsoredDisabled()" in line), f"Free accounts still see ads: {file}"
video = (ROOT / "messenger/video/VideoAds.java").read_text(encoding="utf-8")
init = video.split("private void init(BulletinFactory bulletinFactory) {", 1)[1]
assert init.index("isSponsoredDisabled()") < init.index("if (cache.loaded)"), "Cached video ads must not be scheduled"
print("PASS: channel, bot, search and video ads disabled, including cache")
