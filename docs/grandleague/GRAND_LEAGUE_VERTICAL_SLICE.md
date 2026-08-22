# Grand League vertical slice

Local acceptance gate: `qa/grandleague/run.sh`

Currently proven locally:
- event-driven task completion and exact-once points
- tier progression and exact-once tier rewards
- one selected traditional relic per tier
- region-token unlocks
- fragment unlock/equip and fragment-set activation
- combat mastery prerequisite graph and point spending
- Demonic Pact prerequisite graph and point spending
- Echo boss eligibility and kill records
- versioned dependency-free persistence round trip
- player login/event/save/relog adapter
- task/UI overview view models
- shared-trigger regression protection
- 2,500-task / 250,000-signal stress acceptance

Bootstrap content is intentionally small. It exists to prove the entire mode loop before the historical task/relic corpus is imported.
