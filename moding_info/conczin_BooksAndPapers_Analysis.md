# Books and Papers - Deep Analysis

**Mod Name:** Books and Papers
**Version:** 1.1.0
**Author:** Conczin (conczin@gmail.com)
**Website:** https://conczin.net/
**Architecture:** Java Plugin + Asset Pack (11MB)

## Overview

A comprehensive book and mail system featuring writable multi-page books, lecterns for book display, and a player-to-player mail system. This mod demonstrates advanced Hytale modding techniques including custom UI pages, world-level resource storage, and item metadata manipulation.

---

## Feature Summary

| Feature | Description |
|---------|-------------|
| Writable Books | Multi-page books with title and content per page |
| Book Signing | Lock books with author attribution |
| Lecterns | Place books on lecterns for public reading |
| Mailbox System | Send items (especially books) to other players |
| Book Styles | Multiple book appearances (Book, Demon, Herbalist, Letter, Parchment) |
| Copy Mechanic | Copy book contents to blank books at lecterns |

---

## File Structure

```
books-and-papers-1.1.0.jar (11MB)
├── manifest.json
├── net/conczin/
│   ├── BooksAndPapers.java           # Main plugin
│   ├── LecternInteraction.java       # Lectern right-click handler
│   ├── MailboxInteraction.java       # Mailbox right-click handler
│   ├── Parser.java                   # Text parsing for formatting
│   ├── data/
│   │   ├── BookData.java             # Book content storage
│   │   └── MailboxResource.java      # World-level mail storage
│   ├── gui/
│   │   ├── BooksGui.java             # Book reading/editing UI
│   │   ├── BookSignGui.java          # Book signing UI
│   │   ├── BookUISupplier.java       # CustomPageSupplier impl
│   │   ├── CodecDataInteractiveUIPage.java  # Base class for UIs
│   │   └── MailComposeGui.java       # Mail composition UI
│   └── utils/
│       ├── Utils.java                # Helper functions
│       ├── RecordCodec.java          # Custom codec utilities
│       ├── ListCodec.java            # List codec helper
│       └── Functions.java            # Functional interfaces
├── Common/
│   ├── Blocks/                       # Lectern, Mailbox models
│   ├── Items/                        # Book models
│   ├── Icons/ItemsGenerated/         # Item icons
│   ├── Sounds/BooksAndPapers/        # Sound effects
│   └── UI/Custom/
│       ├── Common/                   # High-res book backgrounds
│       └── Pages/BooksAndPapers/     # UI definitions
│           ├── BookBase.ui           # Book UI layout
│           ├── BookSign.ui           # Sign dialog
│           ├── Compose.ui            # Mail compose
│           └── Styles/               # Different book styles
└── Server/
    ├── Audio/SoundEvents/            # Sound definitions
    ├── Item/Items/                   # Item definitions
    │   ├── Books/                    # Book items
    │   ├── Books_And_Papers_Lectern.json
    │   └── Books_And_Papers_Mailbox.json
    └── Languages/en-US/server.lang   # Translations
```

---

## Plugin Architecture

### Main Plugin (BooksAndPapers.java)

```java
public class BooksAndPapers extends JavaPlugin {
    private static BooksAndPapers instance;
    private ResourceType<EntityStore, MailboxResource> mailbox;

    public BooksAndPapers(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    protected void setup() {
        // Register world-level resource for mailboxes
        this.mailbox = this.getEntityStoreRegistry().registerResource(
            MailboxResource.class,
            "BooksAndPapersMailboxes",
            MailboxResource.CODEC
        );

        // Register custom interactions
        this.getCodecRegistry(Interaction.CODEC).register(
            "Books_And_Papers_Mailbox",
            MailboxInteraction.class,
            MailboxInteraction.CODEC
        );

        this.getCodecRegistry(Interaction.CODEC).register(
            "Books_And_Papers_Lectern",
            LecternInteraction.class,
            LecternInteraction.CODEC
        );

        // Register custom UI page supplier
        this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC).register(
            "Books_And_Papers_Book",
            BookUISupplier.class,
            BookUISupplier.CODEC
        );
    }

    public static BooksAndPapers getInstance() { return instance; }
    public ResourceType<EntityStore, MailboxResource> getMailbox() { return this.mailbox; }
}
```

### Key Registrations

| Type | ID | Class | Purpose |
|------|-----|-------|---------|
| Interaction | `Books_And_Papers_Mailbox` | `MailboxInteraction` | Mailbox click handler |
| Interaction | `Books_And_Papers_Lectern` | `LecternInteraction` | Lectern click handler |
| PageSupplier | `Books_And_Papers_Book` | `BookUISupplier` | Creates book UI pages |
| Resource | `BooksAndPapersMailboxes` | `MailboxResource` | World-level mail storage |

---

## Custom UI System

### Base Class: CodecDataInteractiveUIPage<T>

This abstract class enables custom UI pages with typed data binding:

```java
public abstract class CodecDataInteractiveUIPage<T> extends CustomUIPage {
    protected final Codec<T> eventDataCodec;

    public CodecDataInteractiveUIPage(
        PlayerRef playerRef,
        CustomPageLifetime lifetime,
        Codec<T> eventDataCodec
    ) {
        super(playerRef, lifetime);
        this.eventDataCodec = eventDataCodec;
    }

    // Override to handle typed events from UI
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, T data) {
    }

    // Send UI updates to client
    protected void sendUpdate(UICommandBuilder commandBuilder,
                              UIEventBuilder eventBuilder,
                              boolean clear) {
        Ref ref = this.playerRef.getReference();
        if (ref != null) {
            Store store = ref.getStore();
            World world = ((EntityStore)store.getExternalData()).getWorld();
            world.execute(() -> {
                Player player = (Player)store.getComponent(ref, Player.getComponentType());
                player.getPageManager().updateCustomPage(new CustomPage(
                    this.getClass().getName(),
                    false,
                    clear,
                    this.lifetime,
                    commandBuilder != null ? commandBuilder.getCommands()
                                           : UICommandBuilder.EMPTY_COMMAND_ARRAY,
                    eventBuilder != null ? eventBuilder.getEvents()
                                         : UIEventBuilder.EMPTY_EVENT_BINDING_ARRAY
                ));
            });
        }
    }

    // Parse raw JSON from UI into typed data
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, String rawData) {
        ExtraInfo extrainfo = ExtraInfo.THREAD_LOCAL.get();
        T t = this.eventDataCodec.decodeJson(new RawJsonReader(rawData.toCharArray()), extrainfo);
        this.handleDataEvent(ref, store, t);
    }
}
```

### BooksGui Implementation

```java
public class BooksGui extends CodecDataInteractiveUIPage<Data> {
    private boolean editMode = true;
    private int page = 0;
    private final BlockPosition block;  // null if holding book, position if in lectern
    private final String style;
    private final String background;

    // Data record for UI events
    public record Data(String title, String content, String action) {
        public static final Codec<Data> CODEC = RecordCodec.composite(
            "@Title", Codec.STRING, Data::title,
            "@Content", Codec.STRING, Data::content,
            "Action", Codec.STRING, Data::action,
            Data::new
        );
    }

    public BooksGui(PlayerRef playerRef, BlockPosition block, String style, String background) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.block = block;
        this.style = style;
        this.background = background;
    }

    // Build initial UI
    public void build(Ref<EntityStore> ref,
                      UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder,
                      Store<EntityStore> store) {
        // Load style-specific UI template
        commandBuilder.append("Pages/BooksAndPapers/Styles/" + this.style + ".ui");

        // Bind UI events
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged,
            "#Title", EventData.of("@Title", "#Title.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged,
            "#Content", EventData.of("@Content", "#Content.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#Previous", EventData.of("Action", "Previous"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#Next", EventData.of("Action", "Next"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#Sign", EventData.of("Action", "Sign"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#Edit", EventData.of("Action", "Edit"));

        this.buildList(ref, commandBuilder);
    }

    // Update UI with book data
    private void buildList(Ref<EntityStore> ref, UICommandBuilder commandBuilder) {
        BookData book = Utils.getData(ref, this.block, "BookAndPapers_BookData", BookData.CODEC);
        this.editMode = this.editMode && !book.signed;

        if (!this.editMode) {
            this.page = Math.min(this.page, book.pages.size() - 1);
        }

        commandBuilder.set("#Background.Background",
            "Common/" + this.background + ".png");

        BookData.Page page = book.getOrCreatePage(this.page);
        if (this.editMode) {
            commandBuilder.set("#Title.Value", page.title);
            commandBuilder.set("#Content.Value", page.content);
        } else {
            commandBuilder.set("#TitleLabel.TextSpans", Parser.parse(page.title));
            commandBuilder.set("#ContentLabel.TextSpans", Parser.parse(page.content));
        }

        commandBuilder.set("#Title.Visible", this.editMode);
        commandBuilder.set("#TitleLabel.Visible", !this.editMode);
        commandBuilder.set("#Content.Visible", this.editMode);
        commandBuilder.set("#ContentLabel.Visible", !this.editMode);
        commandBuilder.set("#PageNumber.Text",
            String.format("%d/%d", this.page + 1, book.pages.size()));
        commandBuilder.set("#Edit.Visible", !book.signed);
        commandBuilder.set("#Sign.Visible", !book.signed);
    }

    // Handle UI events
    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        super.handleDataEvent(ref, store, data);

        // Save text changes
        if (data.title != null || data.content != null) {
            this.saveBook(ref, data.title, data.content);
        }

        // Handle button actions
        if ("Previous".equals(data.action)) {
            this.page = Math.max(0, this.page - 1);
            this.rebuildPage(ref);
        }
        if ("Next".equals(data.action)) {
            ++this.page;
            this.rebuildPage(ref);
        }
        if ("Edit".equals(data.action)) {
            this.editMode = !this.editMode;
            this.rebuildPage(ref);
        }
        if ("Sign".equals(data.action)) {
            Player player = (Player)store.getComponent(ref, Player.getComponentType());
            player.getPageManager().openCustomPage(ref, store,
                new BookSignGui(this.playerRef, this.block));
        }
    }

    private void saveBook(Ref<EntityStore> ref, String title, String content) {
        BookData book = Utils.getData(ref, this.block, "BookAndPapers_BookData", BookData.CODEC);
        if (book.signed) return;

        BookData.Page page = book.getOrCreatePage(this.page);
        page.title = title == null ? page.title : title;
        page.content = content == null ? page.content : content;
        Utils.setData(ref, this.block, "BookAndPapers_BookData", BookData.CODEC, book);
    }
}
```

---

## Data Storage

### BookData (Item Metadata)

```java
public final class BookData {
    public static final String METADATA_KEY = "BookAndPapers_BookData";

    public static final BuilderCodec<BookData> CODEC = BuilderCodec.builder(BookData.class, BookData::new)
        .appendInherited(new KeyedCodec("Title", Codec.STRING), ...)
        .appendInherited(new KeyedCodec("Pages", new ListCodec<Page>(Page.CODEC)), ...)
        .appendInherited(new KeyedCodec("Author", Codec.STRING), ...)
        .appendInherited(new KeyedCodec("Signed", Codec.BOOLEAN), ...)
        .build();

    public String title = "";
    public List<Page> pages = new LinkedList<Page>();
    public String author = "";
    public boolean signed = false;

    public Page getOrCreatePage(int page) {
        page = Math.max(0, page);
        while (this.pages.size() <= page) {
            this.pages.add(new Page());
        }
        return this.pages.get(page);
    }

    public static final class Page {
        public static final BuilderCodec<Page> CODEC = ...;
        public String title = "";
        public String content = "";
    }
}
```

### MailboxResource (World Resource)

```java
public class MailboxResource implements Resource<EntityStore> {
    public static final BuilderCodec<MailboxResource> CODEC = ...;
    private final Map<UUID, MailBox> mailboxes = new HashMap<UUID, MailBox>();

    public static ResourceType<EntityStore, MailboxResource> getResourceType() {
        return BooksAndPapers.getInstance().getMailbox();
    }

    public void push(UUID playerUuid, ItemStack item) {
        this.mailboxes.computeIfAbsent(playerUuid, uUID -> new MailBox()).push(item);
    }

    public MailBox getMailbox(UUID playerUuid) {
        return this.mailboxes.computeIfAbsent(playerUuid, uUID -> new MailBox());
    }

    public static class MailBox {
        private final List<ItemStack> mails;
        private String playerName;

        public void push(ItemStack item) { this.mails.add(item); }
        public ItemStack pop() {
            if (this.mails.isEmpty()) return null;
            return this.mails.removeFirst();
        }
        public boolean hasMail() { return !this.mails.isEmpty(); }
    }
}
```

---

## Item Metadata Manipulation

### Utils.setData / getData

```java
public static <T> void setData(Ref<EntityStore> ref, BlockPosition block,
                               String field, BuilderCodec<T> codec, T data) {
    if (block == null) {
        // Book in player's hand - modify held item
        Inventory inventory = Utils.getInventory(ref);
        ItemStack itemInHand = inventory.getActiveHotbarItem();
        if (itemInHand != null) {
            ItemStack newItemInHand = itemInHand.withMetadata(field, codec, data);
            inventory.getHotbar().replaceItemStackInSlot(
                (short)inventory.getActiveHotbarSlot(),
                itemInHand,
                newItemInHand
            );
        }
    } else {
        // Book in lectern container - modify container item
        World world = ((EntityStore)ref.getStore().getExternalData()).getWorld();
        ItemStack stack = Utils.getItemFromContainer(world, block, 0);
        if (stack != null) {
            ItemStack newStack = stack.withMetadata(field, codec, data);
            ItemContainerState inventory = Utils.getInventory(world, block);
            if (inventory != null) {
                inventory.getItemContainer().setItemStackForSlot((short)0, newStack);
            }
        }
    }
}

public static <T> T getData(Ref<EntityStore> ref, BlockPosition block,
                            String field, BuilderCodec<T> codec) {
    ItemStack stack;
    if (block == null) {
        Inventory inventory = Utils.getInventory(ref);
        stack = inventory.getActiveHotbarItem();
    } else {
        World world = ((EntityStore)ref.getStore().getExternalData()).getWorld();
        stack = Utils.getItemFromContainer(world, block, 0);
    }
    if (stack != null) {
        return stack.getFromMetadataOrDefault(field, codec);
    }
    return codec.getDefaultValue();
}
```

---

## UI Definition Format (.ui files)

Hytale uses a custom DSL for UI definitions:

### BookBase.ui
```
$C = "../../Common.ui";  // Import common definitions

// Button style template
@EditButton = Button {
  Style: (
    Default: (Background: (TexturePath: "../../Common/Sketched/Edit.png", Color: #ffffff(0.6))),
    Hovered: (Background: (TexturePath: "../../Common/Sketched/Edit.png", Color: #ffffff(0.9))),
    Pressed: (Background: (TexturePath: "../../Common/Sketched/Edit.png", Color: #ffffff(0.8))),
  );
};

// Book layout group
@BookGroup = Group {
    LayoutMode: Top;

    Group #Book {
        LayoutMode: Full;
        Anchor: (Top: 10, Height: 30);

        TextField #Title {
            Anchor: (Left: 20);
            PlaceholderText: %server.customUI.booksAndPapers.title;  // Translation key
            Style: InputFieldStyle(FontName: "Secondary", FontSize: 24, TextColor: #000000(0.8));
            IsReadOnly: false;
            Visible: false;  // Toggled by code
        }

        Label #TitleLabel {
            Anchor: (Full: 0);
            Style: LabelStyle(FontName: "Secondary", FontSize: 24, TextColor: #000000(0.8),
                              HorizontalAlignment: Center, Wrap: false);
        }
    }

    Group {
        LayoutMode: Full;
        FlexWeight: 1;

        MultilineTextField #Content {
            PlaceholderText: %server.customUI.booksAndPapers.empty;
            Style: InputFieldStyle(FontName: "Default", FontSize: 16, TextColor: #000000(0.7));
            ScrollbarStyle: ScrollbarStyle(Size: 0);
        }

        Label #ContentLabel {
            Style: LabelStyle(FontName: "Default", FontSize: 16, TextColor: #000000(0.7),
                              HorizontalAlignment: Start, Wrap: true);
        }
    }

    Group {
        LayoutMode: Center;

        @PreviousButton #Previous { Anchor: (Width: 40, Height: 40); }
        Label #PageNumber { Text: "1/1"; }
        @NextButton #Next { Anchor: (Width: 40, Height: 40); }
    }
};
```

### Style file (Book.ui)
```
$C = "../../../Common.ui";  // Common UI
$B = "../BookBase.ui";      // Base book layout

$C.@PageOverlay {}  // Include overlay from common

Group #Background {
    Anchor: (Width: 700, Height: 800);
    Padding: (Left: 110, Right: 110, Top: 55, Bottom: 85);
    LayoutMode: Full;
    Background: (TexturePath: "../../Common/Book.png");

    $B.@BookGroup {
        Anchor: (Full: 0);
    }
}

$C.@BackButton {}
```

### UI Element Reference

| Element | Purpose | Key Properties |
|---------|---------|----------------|
| `Group` | Container | `LayoutMode`, `Anchor`, `Padding` |
| `Button` | Clickable | `Style` (Default/Hovered/Pressed), `TooltipText` |
| `Label` | Text display | `Text`, `Style`, `TextSpans` (for formatting) |
| `TextField` | Single-line input | `Value`, `PlaceholderText`, `IsReadOnly` |
| `MultilineTextField` | Multi-line input | Same as TextField |
| `#id` | Element ID | Used in code as `#Title`, `#Content` |
| `@Template` | Reusable template | Referenced as `@BookGroup` |
| `$import` | File import | `$C = "../Common.ui"` |
| `%key` | Translation | `%server.customUI.title` |

---

## Item Definition (JSON)

### Book Item
```json
{
  "Parent": "Template_Books_And_Papers",
  "TranslationProperties": {
    "Name": "server.items.Books_And_Papers_Book.name"
  },
  "Icon": "Icons/ItemsGenerated/Books_And_Papers_Book.png",
  "Model": "Items/Books_And_Papers_Book.blockymodel",
  "Texture": "Items/Books_And_Papers_Book.png",
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "Simple",
          "Effects": { "LocalSoundEventId": "SFX_Books_And_Papers_Open" }
        },
        {
          "Type": "OpenCustomUI",
          "Page": {
            "Id": "Books_And_Papers_Book",
            "Style": "Book",
            "Background": "Book"
          }
        }
      ]
    }
  },
  "Recipe": { ... }
}
```

### Lectern Block Item
```json
{
  "TranslationProperties": {
    "Name": "server.items.Books_And_Papers_Lectern.name"
  },
  "BlockType": {
    "CustomModel": "Blocks/Books_And_Papers_Lectern.blockymodel",
    "State": {
      "Id": "container",
      "Capacity": 1  // Holds one book
    },
    "Interactions": {
      "Primary": "Break_Container",
      "Use": {
        "Interactions": [{
          "Type": "Condition",
          "Crouching": true,
          "Next": { "Type": "OpenContainer" },
          "Failed": { "Type": "Books_And_Papers_Lectern" }  // Custom interaction
        }]
      }
    }
  }
}
```

---

## Key Patterns & APIs

### 1. Custom UI Page Supplier

```java
// Implement OpenCustomUIInteraction.CustomPageSupplier
public record BookUISupplier(String style, String background)
    implements OpenCustomUIInteraction.CustomPageSupplier {

    public static final Codec<BookUISupplier> CODEC = RecordCodec.composite(
        BookUISupplier::new,
        new RecordCodec.Field<>("Style", Codec.STRING, BookUISupplier::style, "Book"),
        new RecordCodec.Field<>("Background", Codec.STRING, BookUISupplier::style, "Book")
    );

    public CustomUIPage tryCreate(Ref<EntityStore> ref,
                                   ComponentAccessor<EntityStore> accessor,
                                   PlayerRef playerRef,
                                   InteractionContext context) {
        ItemStack heldItem = context.getHeldItem();
        boolean isItem = heldItem != null && Utils.getBookSupplier(heldItem) != null;
        return new BooksGui(playerRef,
                            isItem ? null : context.getTargetBlock(),
                            this.style,
                            this.background);
    }
}

// Register in setup()
this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC).register(
    "Books_And_Papers_Book",
    BookUISupplier.class,
    BookUISupplier.CODEC
);
```

### 2. World-Level Resource

```java
// Register resource
this.mailbox = this.getEntityStoreRegistry().registerResource(
    MailboxResource.class,
    "BooksAndPapersMailboxes",
    MailboxResource.CODEC
);

// Access resource
MailboxResource resource = (MailboxResource)store.getResource(
    MailboxResource.getResourceType()
);
resource.push(playerUuid, itemStack);
```

### 3. Item Metadata

```java
// Read from item
BookData data = itemStack.getFromMetadataOrDefault("BookAndPapers_BookData", BookData.CODEC);

// Write to item (creates new ItemStack)
ItemStack newStack = itemStack.withMetadata("BookAndPapers_BookData", BookData.CODEC, data);
```

### 4. UI Event Binding

```java
// Bind UI element to data field
eventBuilder.addEventBinding(
    CustomUIEventBindingType.ValueChanged,
    "#Title",                            // Element selector
    EventData.of("@Title", "#Title.Value"),  // Data mapping
    false
);

// Bind button click
eventBuilder.addEventBinding(
    CustomUIEventBindingType.Activating,
    "#Next",
    EventData.of("Action", "Next")
);
```

### 5. UI Commands

```java
// Load UI template
commandBuilder.append("Pages/BooksAndPapers/Styles/Book.ui");

// Set element property
commandBuilder.set("#Title.Value", "Book Title");
commandBuilder.set("#Title.Visible", true);
commandBuilder.set("#Background.Background", "Common/Book.png");
```

---

## Sound System

```java
private static void playSound(CommandBuffer<EntityStore> commandBuffer,
                              Vector3i targetBlock,
                              Ref<EntityStore> ref,
                              String sound) {
    int soundEventIndex = SoundEvent.getAssetMap().getIndex(sound);
    SoundUtil.playSoundEvent3d(ref, soundEventIndex,
        targetBlock.x, targetBlock.y, targetBlock.z,
        commandBuffer);
}
```

---

## Relevance to HytaleVehicles

| Pattern | Application |
|---------|-------------|
| Custom UI Pages | Vehicle dashboard, speedometer, fuel gauge |
| Item Metadata | Store vehicle fuel level, damage, upgrades |
| World Resource | Track all spawned vehicles globally |
| UICommandBuilder | Update dashboard displays in real-time |
| CustomPageSupplier | Open vehicle control panel on interaction |
| BlockPosition handling | Distinguish handheld vs placed vehicle |

---

## Summary

Books and Papers demonstrates:
- **Custom UI system** with `.ui` DSL and event binding
- **Item metadata** for persistent per-item data
- **World resources** for global persistent data
- **Multiple interactions** (Lectern, Mailbox) from one plugin
- **PageSupplier pattern** for dynamic UI creation
- **Sound integration** with 3D positioning

This is one of the most technically sophisticated community mods, showcasing advanced Hytale API usage.
