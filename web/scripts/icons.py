from pathlib import Path
from PIL import Image, ImageDraw

directory = Path(__file__).resolve().parent.parent / 'public' / 'icons'
directory.mkdir(parents=True, exist_ok=True)
for size in (180, 192, 512):
    image = Image.new('RGB', (size, size), '#246BCE')
    draw = ImageDraw.Draw(image)
    draw.line([(size*.24, size*.36), (size*.72, size*.36)], fill='white', width=size//20)
    draw.polygon([(size*.62, size*.24), (size*.77, size*.36), (size*.62, size*.48)], fill='white')
    draw.line([(size*.28, size*.64), (size*.76, size*.64)], fill='white', width=size//20)
    draw.polygon([(size*.38, size*.52), (size*.23, size*.64), (size*.38, size*.76)], fill='white')
    image.save(directory / ('apple-touch-icon.png' if size == 180 else f'icon-{size}.png'))
print('Generated 180, 192 and 512 pixel Relay icons.')
