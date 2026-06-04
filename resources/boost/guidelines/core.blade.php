## musicplayer/media-library

A NativePHP Mobile plugin

### Installation

```bash
composer require musicplayer/media-library
```

### PHP Usage (Livewire/Blade)

Use the `MediaLibrary` facade:

@verbatim
<code-snippet name="Using MediaLibrary Facade" lang="php">
use Musicplayer\MediaLibrary\Facades\MediaLibrary;

// Execute the plugin functionality
$result = MediaLibrary::execute(['option1' => 'value']);

// Get the current status
$status = MediaLibrary::getStatus();
</code-snippet>
@endverbatim

### Available Methods

- `MediaLibrary::execute()`: Execute the plugin functionality
- `MediaLibrary::getStatus()`: Get the current status

### Events

- `MediaLibraryCompleted`: Listen with `#[OnNative(MediaLibraryCompleted::class)]`

@verbatim
<code-snippet name="Listening for MediaLibrary Events" lang="php">
use Native\Mobile\Attributes\OnNative;
use Musicplayer\MediaLibrary\Events\MediaLibraryCompleted;

#[OnNative(MediaLibraryCompleted::class)]
public function handleMediaLibraryCompleted($result, $id = null)
{
    // Handle the event
}
</code-snippet>
@endverbatim

### JavaScript Usage (Vue/React/Inertia)

@verbatim
<code-snippet name="Using MediaLibrary in JavaScript" lang="javascript">
import { mediaLibrary } from '@musicplayer/media-library';

// Execute the plugin functionality
const result = await mediaLibrary.execute({ option1: 'value' });

// Get the current status
const status = await mediaLibrary.getStatus();
</code-snippet>
@endverbatim