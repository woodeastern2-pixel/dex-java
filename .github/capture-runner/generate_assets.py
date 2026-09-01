from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfgen import canvas

out = Path(__file__).resolve().parent / "generated-assets"
out.mkdir(parents=True, exist_ok=True)

# A deliberately fictional document for VeilPic. It contains no real personal information.
w, h = 1080, 1600
image = Image.new("RGB", (w, h), "white")
draw = ImageDraw.Draw(image)
try:
    title_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 56)
    body_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 38)
    small_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 30)
except OSError:
    title_font = body_font = small_font = ImageFont.load_default()

draw.rounded_rectangle((70, 80, 1010, 1510), radius=34, outline=(20, 35, 70), width=5)
draw.text((120, 130), "EW STUDIO · SAMPLE RECEIPT", fill=(15, 31, 67), font=title_font)
draw.line((120, 230, 960, 230), fill=(92, 61, 245), width=6)
rows = [
    ("Customer", "Demo User"),
    ("Phone", "010-0000-1234"),
    ("Email", "demo@easternwood.cloud"),
    ("Order", "EW-2026-0901"),
    ("Address", "Seoul Sample Street 12"),
]
y = 310
for label, value in rows:
    draw.text((130, y), label, fill=(90, 99, 120), font=small_font)
    draw.text((390, y - 8), value, fill=(20, 28, 55), font=body_font)
    draw.line((130, y + 62, 950, y + 62), fill=(226, 229, 238), width=3)
    y += 145

draw.text((130, 1080), "ITEM", fill=(90, 99, 120), font=small_font)
draw.text((130, 1140), "Mobile application design", fill=(20, 28, 55), font=body_font)
draw.text((760, 1140), "₩120,000", fill=(20, 28, 55), font=body_font)
draw.line((130, 1220, 950, 1220), fill=(226, 229, 238), width=3)
draw.text((130, 1280), "This is fictional data created only for an app demo.", fill=(92, 61, 245), font=small_font)
image.save(out / "sample-private-info.png", optimize=True)

# A fictional one-page agreement for SignPDF.
pdf_path = out / "sample-contract.pdf"
c = canvas.Canvas(str(pdf_path), pagesize=A4)
page_w, page_h = A4
c.setFillColorRGB(0.06, 0.12, 0.27)
c.setFont("Helvetica-Bold", 21)
c.drawString(64, page_h - 78, "SERVICE CONFIRMATION")
c.setStrokeColorRGB(0.36, 0.24, 0.96)
c.setLineWidth(3)
c.line(64, page_h - 94, page_w - 64, page_h - 94)
c.setFillColorRGB(0.16, 0.19, 0.29)
c.setFont("Helvetica", 11)
lines = [
    "This sample document is generated solely to demonstrate SignPDF.",
    "Project: Eastern Wood Studio website presentation",
    "Client: Demo Company (fictional)",
    "Date: September 1, 2026",
    "Scope: UI review and delivery confirmation",
]
y = page_h - 135
for line in lines:
    c.drawString(68, y, line)
    y -= 28
c.setFillColorRGB(0.96, 0.96, 0.99)
c.roundRect(64, y - 145, page_w - 128, 120, 12, fill=1, stroke=0)
c.setFillColorRGB(0.16, 0.19, 0.29)
c.setFont("Helvetica", 10)
c.drawString(84, y - 60, "By signing below, the reviewer confirms that this is a fictional demo file.")
c.setStrokeColorRGB(0.45, 0.47, 0.55)
c.setLineWidth(1)
c.line(80, 150, 290, 150)
c.line(330, 150, 520, 150)
c.setFont("Helvetica", 9)
c.drawString(80, 132, "Signature")
c.drawString(330, 132, "Date")
c.save()

print(out)
