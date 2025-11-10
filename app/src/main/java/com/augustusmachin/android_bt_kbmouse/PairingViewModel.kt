// PairingViewModel removed — state & side-effects migrated into Redux store and middleware.
// The class was deleted as part of the migration to make the store the canonical source
// of truth. If you see references to `PairingViewModel` in your code, update them to use
// `StoreProvider.dispatch(...)` and observe `StoreProvider.asStateFlow()` for UI state.

// This file intentionally contains no Kotlin declarations to avoid reintroducing the
// old symbol. Remove this file entirely once the build/CI confirms no remaining callers.
