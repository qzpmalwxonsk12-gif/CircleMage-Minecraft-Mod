# Procedurally generates all custom Circle Mage textures (particles, entity skins, GUI)
# using GDI+. Re-run to regenerate after tweaking the parameters below.
#   powershell -ExecutionPolicy Bypass -File tools\gen_textures.ps1
Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot "..\src\main\resources\assets\circlemage\textures"
$root = [System.IO.Path]::GetFullPath($root)
New-Item -ItemType Directory -Force -Path "$root\particle" | Out-Null
New-Item -ItemType Directory -Force -Path "$root\entity"   | Out-Null
New-Item -ItemType Directory -Force -Path "$root\gui"      | Out-Null
New-Item -ItemType Directory -Force -Path "$root\item"     | Out-Null

function Clamp([double]$v){ if($v -lt 0){return 0} elseif($v -gt 255){return 255} else {return [int]$v} }
function Lerp([double]$a,[double]$b,[double]$t){ return $a + ($b-$a)*$t }
function Gauss([double]$x,[double]$s){ return [math]::Exp(-($x*$x)/(2.0*$s*$s)) }

# Linear gradient over t in [0,1]. $stops = @(@(pos,r,g,b),...) ascending by pos. Returns @(r,g,b).
function Grad([double]$t,$stops){
    if($t -le $stops[0][0]){ return @($stops[0][1],$stops[0][2],$stops[0][3]) }
    $n=$stops.Count
    $last=$stops[$n-1]
    if($t -ge $last[0]){ return @($last[1],$last[2],$last[3]) }
    for($i=0;$i -lt $n-1;$i++){
        $a=$stops[$i]; $b=$stops[$i+1]
        if($t -ge $a[0] -and $t -le $b[0]){
            $f=($t-$a[0])/($b[0]-$a[0])
            return @([int](Lerp $a[1] $b[1] $f),[int](Lerp $a[2] $b[2] $f),[int](Lerp $a[3] $b[3] $f))
        }
    }
    return @($last[1],$last[2],$last[3])
}

# Per-pixel texture builder. $pix is a scriptblock: param($x,$y,$w,$h) -> @(r,g,b,a) or $null.
function Make([int]$w,[int]$h,[string]$path,[scriptblock]$pix){
    $bmp = New-Object System.Drawing.Bitmap($w,$h,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for($y=0;$y -lt $h;$y++){
        for($x=0;$x -lt $w;$x++){
            $c = & $pix $x $y $w $h
            if($c -ne $null){
                $bmp.SetPixel($x,$y,[System.Drawing.Color]::FromArgb((Clamp $c[3]),(Clamp $c[0]),(Clamp $c[1]),(Clamp $c[2])))
            }
        }
    }
    $bmp.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "  wrote $([System.IO.Path]::GetFileName($path))"
}

$P = "$root\particle"
$E = "$root\entity"
$G = "$root\gui"
$I = "$root\item"

Write-Host "Particles ->"

# --- arcane: white core, cyan 4-point sparkle star ---
Make 32 32 "$P\arcane.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)
    $glow=Gauss $d 3.6
    $barH=(Gauss $dy 1.0)*(Gauss $dx 7.5)
    $barV=(Gauss $dx 1.0)*(Gauss $dy 7.5)
    $du=($dx-$dy)/1.41421; $dv=($dx+$dy)/1.41421
    $diag=0.45*((Gauss $du 0.9)*(Gauss $dv 5.0)+(Gauss $dv 0.9)*(Gauss $du 5.0))
    $i=$glow+0.8*[math]::Max($barH,$barV)+$diag
    if($i -lt 0.04){ return $null }
    $t=[math]::Min(1.0,$d/13.0)
    $rgb=Grad $t @(@(0.0,255,255,255),@(0.4,150,235,255),@(1.0,40,150,255))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(255,$i*260))
}

# --- mote: simple soft round glow (white) ---
Make 32 32 "$P\mote.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)
    $i=Gauss $d 4.5
    if($i -lt 0.04){ return $null }
    $t=[math]::Min(1.0,$d/14.0)
    $rgb=Grad $t @(@(0.0,255,255,255),@(1.0,170,225,255))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(255,$i*255))
}

# --- ember: hot fire mote, slight upward teardrop ---
Make 32 32 "$P\ember.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0+1.5; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $sy = if($dy -lt 0){ $dy*1.6 } else { $dy*0.9 }   # taller upward (flame)
    $d=[math]::Sqrt($dx*$dx+$sy*$sy)
    $i=Gauss $d 4.0
    if($i -lt 0.05){ return $null }
    $t=[math]::Min(1.0,$d/13.0)
    $rgb=Grad $t @(@(0.0,255,250,225),@(0.3,255,214,90),@(0.65,255,120,30),@(1.0,150,20,0))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(255,$i*255))
}

# --- frost: pale-blue 6-armed snowflake ---
Make 32 32 "$P\frost.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)
    $glow=(Gauss $d 3.0)*0.9
    $arm=0.0
    foreach($a in 0.0,1.0471975,2.0943951){
        $ca=[math]::Cos($a); $sa=[math]::Sin($a)
        $u=$dx*$ca+$dy*$sa; $v=-$dx*$sa+$dy*$ca
        $arm=[math]::Max($arm,(Gauss $v 0.8)*(Gauss $u 6.0))
    }
    $i=$glow+0.85*$arm
    if($i -lt 0.05){ return $null }
    $t=[math]::Min(1.0,$d/13.0)
    $rgb=Grad $t @(@(0.0,240,252,255),@(0.5,165,225,255),@(1.0,90,170,235))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(255,$i*255))
}

# --- spark: sharp electric 4-point + diagonal, white/yellow ---
Make 32 32 "$P\spark.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)
    $core=Gauss $d 1.8
    $barH=(Gauss $dy 0.7)*(Gauss $dx 6.5)
    $barV=(Gauss $dx 0.7)*(Gauss $dy 6.5)
    $i=$core+[math]::Max($barH,$barV)
    if($i -lt 0.05){ return $null }
    $t=[math]::Min(1.0,$d/13.0)
    $rgb=Grad $t @(@(0.0,255,255,255),@(0.4,255,245,170),@(1.0,255,210,70))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(255,$i*255))
}

# --- gale: soft horizontal wisp, faint green-white ---
Make 32 32 "$P\gale.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $i=(Gauss $dx 8.0)*(Gauss $dy 2.6)
    if($i -lt 0.05){ return $null }
    $t=[math]::Min(1.0,([math]::Abs($dx))/14.0)
    $rgb=Grad $t @(@(0.0,255,255,255),@(1.0,200,255,220))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(220,$i*230))
}

# --- radiance: soft white-gold flare with long faint spikes ---
Make 32 32 "$P\radiance.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)
    $glow=Gauss $d 4.2
    $barH=(Gauss $dy 1.3)*(Gauss $dx 10.0)
    $barV=(Gauss $dx 1.3)*(Gauss $dy 10.0)
    $i=$glow+0.55*[math]::Max($barH,$barV)
    if($i -lt 0.04){ return $null }
    $t=[math]::Min(1.0,$d/14.0)
    $rgb=Grad $t @(@(0.0,255,255,255),@(0.5,255,245,205),@(1.0,255,215,120))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(255,$i*255))
}

# Graphics-based glyphs (rune, hex) with a soft underglow.
function MakeGraphics([int]$w,[int]$h,[string]$path,[scriptblock]$draw){
    $bmp = New-Object System.Drawing.Bitmap($w,$h,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::FromArgb(0,0,0,0))
    & $draw $g $w $h
    $g.Dispose()
    $bmp.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "  wrote $([System.IO.Path]::GetFileName($path))"
}

# --- rune: white arcane glyph (ring + inscribed triangle + ticks), tinted in-game ---
MakeGraphics 32 32 "$P\rune.png" {
    param($g,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0
    function PenW($a,$wd){ return New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($a,255,255,255)),$wd }
    # underglow
    $g.DrawEllipse((PenW 60 4.0), 4,4,($w-8),($h-8))
    $g.DrawEllipse((PenW 255 1.6), 5,5,($w-10),($h-10))
    $g.DrawEllipse((PenW 150 1.2), 9,9,($w-18),($h-18))
    # inscribed triangle
    $R=($w/2.0-6)
    $pts=@()
    foreach($k in 0,1,2){
        $ang=-1.5707963 + $k*2.0943951
        $pts += (New-Object System.Drawing.PointF (([float]($cx+[math]::Cos($ang)*$R)),([float]($cy+[math]::Sin($ang)*$R))))
    }
    $g.DrawPolygon((PenW 220 1.4), $pts)
    # outer tick marks
    for($k=0;$k -lt 6;$k++){
        $ang=$k*1.0471975
        $x1=$cx+[math]::Cos($ang)*($w/2-3); $y1=$cy+[math]::Sin($ang)*($w/2-3)
        $x2=$cx+[math]::Cos($ang)*($w/2-1); $y2=$cy+[math]::Sin($ang)*($w/2-1)
        $g.DrawLine((PenW 200 1.4), ([float]$x1),([float]$y1),([float]$x2),([float]$y2))
    }
}

# --- hex: hexagon outline (cyan) used for barrier shimmer ---
MakeGraphics 32 32 "$P\hex.png" {
    param($g,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $R=$w/2.0-3
    $pts=@()
    for($k=0;$k -lt 6;$k++){
        $ang=$k*1.0471975 + 0.5235988
        $pts += (New-Object System.Drawing.PointF (([float]($cx+[math]::Cos($ang)*$R)),([float]($cy+[math]::Sin($ang)*$R))))
    }
    $pen2=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(70,150,240,255)),5.0
    $g.DrawPolygon($pen2,$pts)
    $pen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,190,245,255)),1.8
    $g.DrawPolygon($pen,$pts)
}

Write-Host "Entity ->"

# --- missile orb: bright white core -> cyan halo ---
Make 64 64 "$E\magic_missile.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)/($w/2.0)
    if($d -ge 1.0){ return $null }
    $core=[math]::Pow([math]::Max(0.0,1.0-$d/0.45),2.0)
    $halo=Gauss ($d*$w/2.0) 11.0
    $ring=Gauss (($d-0.55)*$w/2.0) 2.2
    $i=[math]::Min(1.0,$core+0.85*$halo+0.5*$ring)
    if($i -lt 0.03){ return $null }
    $rgb=Grad $d @(@(0.0,255,255,255),@(0.4,180,240,255),@(0.75,80,190,255),@(1.0,30,120,230))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(255,$i*255))
}

# --- barrier dome: cyan hex-grid energy field on transparent ---
MakeGraphics 128 128 "$E\barrier_dome.png" {
    param($g,$w,$h)
    # faint blue wash
    $br=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(26,120,220,255))
    $g.FillRectangle($br,0,0,$w,$h)
    $R=11.0; $dx=$R*1.5; $dy=$R*[math]::Sqrt(3.0)
    $penG=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(45,150,235,255)),3.0
    $pen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(150,190,245,255)),1.3
    for($col=-1;$col -lt [int]($w/$dx)+2;$col++){
        for($row=-1;$row -lt [int]($h/$dy)+2;$row++){
            $cx=$col*$dx
            $cy=$row*$dy + (([math]::Abs($col%2)) * $dy/2.0)
            $pts=@()
            for($k=0;$k -lt 6;$k++){
                $a=$k*1.0471975
                $pts += (New-Object System.Drawing.PointF (([float]($cx+[math]::Cos($a)*$R)),([float]($cy+[math]::Sin($a)*$R))))
            }
            $g.DrawPolygon($penG,$pts)
            $g.DrawPolygon($pen,$pts)
        }
    }
}

# --- barrier panel: rune-grid wall face ---
MakeGraphics 128 128 "$E\barrier_panel.png" {
    param($g,$w,$h)
    $br=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(30,110,210,255))
    $g.FillRectangle($br,0,0,$w,$h)
    $pen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(120,180,240,255)),1.4
    $penB=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(220,200,245,255)),2.2
    # grid
    for($i=0;$i -le 4;$i++){
        $p=$i*($w/4.0)
        $g.DrawLine($pen,([float]$p),0.0,([float]$p),([float]$h))
        $g.DrawLine($pen,0.0,([float]$p),([float]$w),([float]$p))
    }
    # border
    $g.DrawRectangle($penB,1,1,($w-3),($h-3))
    # central rune circle + triangle
    $cx=$w/2.0; $cy=$h/2.0; $R=28.0
    $g.DrawEllipse($penB,([float]($cx-$R)),([float]($cy-$R)),([float]($R*2)),([float]($R*2)))
    $pts=@()
    foreach($k in 0,1,2){
        $a=-1.5707963 + $k*2.0943951
        $pts += (New-Object System.Drawing.PointF (([float]($cx+[math]::Cos($a)*$R)),([float]($cy+[math]::Sin($a)*$R))))
    }
    $g.DrawPolygon($pen,$pts)
}

Write-Host "GUI ->"

# --- mana orb icon ---
Make 16 16 "$G\mana_orb.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)/($w/2.0)
    if($d -ge 1.0){ return $null }
    $i=[math]::Pow(1.0-$d,0.8)
    $rgb=Grad $d @(@(0.0,235,250,255),@(0.5,90,190,255),@(1.0,30,90,200))
    # highlight
    $hx=$dx+2.5; $hy=$dy+2.5; $hl=Gauss ([math]::Sqrt($hx*$hx+$hy*$hy)) 2.0
    $r=[math]::Min(255,$rgb[0]+$hl*160); $gn=[math]::Min(255,$rgb[1]+$hl*160); $b=[math]::Min(255,$rgb[2]+$hl*120)
    return @($r,$gn,$b,[math]::Min(255,$i*255))
}

# --- mana bar frame: dark track + ornate cyan border + corner studs (104x14) ---
MakeGraphics 104 14 "$G\mana_frame.png" {
    param($g,$w,$h)
    $track=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(235,12,14,30))
    $g.FillRectangle($track,2,2,($w-4),($h-4))
    $penO=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,20,28,60)),3.0
    $g.DrawRectangle($penO,1,1,($w-3),($h-3))
    $pen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,120,200,255)),1.3
    $g.DrawRectangle($pen,1,1,($w-3),($h-3))
    # corner studs
    $stud=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,170,230,255))
    foreach($cxy in @(@(1,1),@(($w-4),1),@(1,($h-4)),@(($w-4),($h-4)))){
        $g.FillRectangle($stud,$cxy[0],$cxy[1],3,3)
    }
}

# --- mana bar fill: bright cyan -> deep blue gradient with top sheen (100x10) ---
Make 100 10 "$G\mana_fill.png" {
    param($x,$y,$w,$h)
    $t=$x/[double]($w-1)
    $rgb=Grad $t @(@(0.0,140,235,255),@(0.6,60,150,235),@(1.0,40,90,200))
    $sheen = if($y -le 2){ 60 } elseif($y -ge ($h-2)){ -30 } else { 0 }
    return @([math]::Min(255,$rgb[0]+$sheen),[math]::Min(255,$rgb[1]+$sheen),[math]::Min(255,$rgb[2]+$sheen),255)
}

# --- void: dark-violet orb glow with magenta rim (Dark Orb particles) ---
Make 32 32 "$P\void.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)
    $i=Gauss $d 4.5
    if($i -lt 0.05){ return $null }
    $t=[math]::Min(1.0,$d/13.0)
    $rgb=Grad $t @(@(0.0,70,25,105),@(0.45,120,40,175),@(0.8,205,75,240),@(1.0,150,40,205))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(235,$i*255))
}

Write-Host "Entity (transforms) ->"

# --- spear of light: leaf-bladed lance, white-hot core -> gold edge, with a glowing halo ---
Make 48 128 "$E\spear.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $dx=$x+0.5-$cx; $ty=$y+0.5
    # half-width profile down the length: sharp tip, wide leaf blade, thin shaft, tapered tail
    $hw=0.0
    if($ty -ge 4 -and $ty -lt 34){ $hw = (($ty-4)/30.0) * 14.0 }
    elseif($ty -ge 34 -and $ty -lt 60){ $hw = 14.0 - (($ty-34)/26.0)*11.0 }
    elseif($ty -ge 60 -and $ty -lt 118){ $hw = 3.0 }
    elseif($ty -ge 118 -and $ty -lt 126){ $hw = 3.0 - (($ty-118)/8.0)*3.0 }
    $absdx=[math]::Abs($dx)
    if($hw -gt 0.3 -and $absdx -le $hw){
        $edge=$absdx/$hw
        $rgb=Grad $edge @(@(0.0,255,255,255),@(0.45,255,247,210),@(1.0,255,208,115))
        $a=[math]::Min(1.0,1.0-$edge*0.55)
        return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(255,$a*255))
    }
    # outer glow halo (and a soft beam beyond the tip / tail)
    $refHw = if($hw -gt 0.3){ $hw } else { 2.0 }
    $outside = $absdx - $refHw
    $glowX = if($outside -lt 0){ 1.0 } else { [math]::Exp(-($outside*$outside)/(2.0*10.0)) }
    $glowY = 1.0
    if($ty -lt 4){ $glowY = Gauss (4-$ty) 5.0 }
    elseif($ty -gt 126){ $glowY = Gauss ($ty-126) 5.0 }
    $g = $glowX * $glowY
    if($g -lt 0.06){ return $null }
    return @(255,228,150,[math]::Min(175,$g*175))
}

# --- dark orb: void sphere with magenta rim ---
Make 64 64 "$E\dark_orb.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)/($w/2.0)
    if($d -ge 1.0){ return $null }
    $rgb=Grad $d @(@(0.0,28,10,44),@(0.55,62,22,98),@(0.82,155,52,205),@(1.0,95,28,145))
    $a=1.0
    if($d -gt 0.82){ $a=(1.0-$d)/0.18 }
    $hx=$dx+3.0; $hy=$dy+3.0; $hl=Gauss ([math]::Sqrt($hx*$hx+$hy*$hy)) 4.0
    $r=[math]::Min(255,$rgb[0]+$hl*120); $g=[math]::Min(255,$rgb[1]+$hl*80); $b=[math]::Min(255,$rgb[2]+$hl*150)
    return @($r,$g,$b,[math]::Min(255,$a*255))
}

# --- smoke: soft grey puff (aftermath cloud) ---
Make 32 32 "$P\smoke.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)
    $i=Gauss $d 5.0
    if($i -lt 0.05){ return $null }
    $t=[math]::Min(1.0,$d/15.0)
    $rgb=Grad $t @(@(0.0,150,150,160),@(0.6,95,95,108),@(1.0,55,55,66))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(190,$i*210))
}

# --- dust: small tan mote (debris kicked up by the blast) ---
Make 16 16 "$P\dust.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $cy=$h/2.0; $dx=$x+0.5-$cx; $dy=$y+0.5-$cy
    $d=[math]::Sqrt($dx*$dx+$dy*$dy)
    $i=Gauss $d 2.6
    if($i -lt 0.06){ return $null }
    $t=[math]::Min(1.0,$d/7.0)
    $rgb=Grad $t @(@(0.0,170,150,120),@(1.0,110,95,72))
    return @($rgb[0],$rgb[1],$rgb[2],[math]::Min(220,$i*230))
}

# --- spatial rend: vertical rainbow nebula with star speckle (Law of Space) ---
Make 32 128 "$E\spatial_rend.png" {
    param($x,$y,$w,$h)
    $cx=$w/2.0; $dx=[math]::Abs($x+0.5-$cx)/$cx
    $t=$y/[double]$h
    $r=([math]::Sin($t*6.2832+0.0)*0.5+0.5)*255
    $g=([math]::Sin($t*6.2832+2.094)*0.5+0.5)*255
    $b=([math]::Sin($t*6.2832+4.188)*0.5+0.5)*255
    $star=0
    if((Get-Random -Maximum 100) -lt 4){ $star=200 }
    $r=[math]::Min(255,$r*0.72+$star); $g=[math]::Min(255,$g*0.72+$star); $b=[math]::Min(255,$b*0.72+$star)
    $a=(1.0-$dx*0.55)*230
    return @($r,$g,$b,$a)
}

Write-Host "Entity (wandering mages) ->"

# --- wandering mage skin: a robed humanoid on the standard 64x64 biped/player skin layout ---
# Whole skin is robe-coloured, the head + hat layer form a hood/cowl, with a skin-toned face,
# a belt + robe stripe in the trim colour, and dark boots. $robe/$trim/$skin/$hood are @(r,g,b).
function MakeMageSkin([string]$path,$robe,$trim,$skin,$hood){
    MakeGraphics 64 64 $path {
        param($g,$w,$h)
        $robeB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$robe[0],$robe[1],$robe[2]))
        $hoodB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$hood[0],$hood[1],$hood[2]))
        $skinB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$skin[0],$skin[1],$skin[2]))
        $trimB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$trim[0],$trim[1],$trim[2]))
        $bootB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,40,32,30))
        $eyeB =New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,18,18,28))
        $g.FillRectangle($robeB,0,0,$w,$h)        # robe covers body/arms/legs on every face
        $g.FillRectangle($hoodB,0,0,32,16)        # head cube faces -> hood
        $g.FillRectangle($hoodB,32,0,32,16)       # hat overlay -> raised cowl
        $g.FillRectangle($skinB,8,9,8,7)          # face on the head's front face (brow stays hood)
        $g.FillRectangle($eyeB,10,12,2,2)         # eyes
        $g.FillRectangle($eyeB,13,12,2,2)
        $g.FillRectangle($trimB,16,26,40,2)       # belt across torso + right arm
        $g.FillRectangle($trimB,32,58,16,2)       # belt across the left-arm region
        $g.FillRectangle($trimB,23,20,3,12)       # robe stripe down the body front
        $g.FillRectangle($bootB,0,28,16,4)        # right leg boot
        $g.FillRectangle($bootB,16,60,16,4)       # left leg boot
    }
}

MakeMageSkin "$E\wandering_mage_c1.png"      @(46,74,150)  @(205,212,232) @(222,182,150) @(28,42,86)
MakeMageSkin "$E\wandering_mage_c2.png"      @(96,54,146)  @(224,192,96)  @(222,182,150) @(54,30,84)
MakeMageSkin "$E\wandering_mage_c3.png"      @(156,44,58)  @(232,200,96)  @(220,178,148) @(92,26,36)
MakeMageSkin "$E\wandering_mage_prodigy.png" @(26,26,38)   @(255,216,92)  @(232,206,182) @(12,12,18)
MakeMageSkin "$E\the_master.png"             @(18,16,30)   @(255,205,70)  @(226,206,186) @(8,8,14)

Write-Host "Item ->"

# --- mana crystal: a faceted blue gem with light/dark facets, bright outline and a sparkle ---
MakeGraphics 16 16 "$I\mana_crystal.png" {
    param($g,$w,$h)
    function PF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }
    $outline = @((PF 8 0),(PF 13 5),(PF 11 13),(PF 8 16),(PF 5 13),(PF 3 5))
    $body=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,60,140,230))
    $g.FillPolygon($body,$outline)
    # left facet (lighter), right facet (darker) -> a cut-gem read
    $left = @((PF 8 0),(PF 3 5),(PF 5 13),(PF 8 16))
    $lb=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,120,200,255))
    $g.FillPolygon($lb,$left)
    $right = @((PF 8 0),(PF 13 5),(PF 11 13),(PF 8 16))
    $rb=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,38,98,196))
    $g.FillPolygon($rb,$right)
    # bright cyan outline
    $pen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,200,240,255)),1.0
    $g.DrawPolygon($pen,$outline)
    # facet seams from the top tip
    $seam=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(160,210,240,255)),1.0
    $g.DrawLine($seam,([float]8),([float]0),([float]8),([float]16))
    $g.DrawLine($seam,([float]3),([float]5),([float]13),([float]5))
    # white sparkle highlight
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(235,255,255,255))
    $g.FillRectangle($spark,6,3,1,3)
    $g.FillRectangle($spark,5,4,3,1)
}

Write-Host "Item (arcane textiles) ->"

# --- arcane string: a glowing cyan thread loop (item glint added in code) ---
MakeGraphics 16 16 "$I\arcane_string.png" {
    param($g,$w,$h)
    $glow=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(90,120,210,255)),3.2
    $g.DrawEllipse($glow,4,4,8,8)
    $pen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,120,210,255)),1.6
    $g.DrawEllipse($pen,4,4,8,8)
    $g.DrawLine($pen,([float]3),([float]12),([float]7),([float]9))
    $g.DrawLine($pen,([float]9),([float]7),([float]13),([float]4))
    $hi=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(220,225,245,255)),0.9
    $g.DrawEllipse($hi,5,5,5,5)
}

# --- arcane yarn: a wound ball of arcane thread ---
MakeGraphics 16 16 "$I\arcane_yarn.png" {
    param($g,$w,$h)
    $g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,90,150,235))),2,2,12,12)
    $wrap=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,165,215,255)),1.0
    $g.DrawArc($wrap,2,2,12,12,20,160)
    $g.DrawArc($wrap,2,2,12,12,200,160)
    $g.DrawLine($wrap,([float]4),([float]4),([float]12),([float]11))
    $g.DrawLine($wrap,([float]12),([float]4),([float]4),([float]11))
    $g.DrawEllipse((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,205,235,255)),1.0),2,2,12,12)
}

# --- mage robe item icon: a hanging robe, tinted per variant ---
function MakeRobeIcon([string]$path,$robe,$trim){
    MakeGraphics 16 16 $path {
        param($g,$w,$h)
        function PF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }
        $robeB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$robe[0],$robe[1],$robe[2]))
        $trimB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$trim[0],$trim[1],$trim[2]))
        $body=@((PF 6 2),(PF 10 2),(PF 13 14),(PF 3 14))
        $g.FillPolygon($robeB,$body)
        $g.FillPolygon($robeB,@((PF 6 3),(PF 3 9),(PF 5 9),(PF 7 5)))   # left sleeve
        $g.FillPolygon($robeB,@((PF 10 3),(PF 13 9),(PF 11 9),(PF 9 5))) # right sleeve
        $g.FillPolygon($trimB,@((PF 6 2),(PF 10 2),(PF 8 5)))            # collar
        $g.FillRectangle($trimB,7,5,2,9)                                # front trim stripe
        $g.FillRectangle($trimB,4,9,8,1)                                # belt
        $g.DrawPolygon((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,20,20,30)),0.9),$body)
    }
}
MakeRobeIcon "$I\mage_robe_iron.png"     @(150,160,178) @(215,220,230)
MakeRobeIcon "$I\mage_robe_amethyst.png" @(150,92,200)  @(215,175,242)
MakeRobeIcon "$I\mage_robe_emerald.png"  @(60,172,92)   @(175,238,185)
MakeRobeIcon "$I\mage_robe_diamond.png"  @(92,202,226)  @(205,242,250)

Write-Host "Armor (worn robes) ->"
$armorRoot=[System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\src\main\resources\assets\minecraft\textures\models\armor"))
New-Item -ItemType Directory -Force -Path $armorRoot | Out-Null

# Worn chestplate layer (64x32): robe-coloured torso/arms with a trim band, collar and hem.
function MakeArmorLayer([string]$path,$robe,$trim){
    MakeGraphics 64 32 $path {
        param($g,$w,$h)
        $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$robe[0],$robe[1],$robe[2]))),0,0,$w,$h)
        $trimB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$trim[0],$trim[1],$trim[2]))
        $g.FillRectangle($trimB,16,16,40,1)   # collar line across the torso/arm strip
        $g.FillRectangle($trimB,16,30,40,1)   # hem
        $g.FillRectangle($trimB,26,16,2,16)   # front trim stripe down the torso
    }
}
function MakeEmptyLayer([string]$path){ Make 64 32 $path { param($x,$y,$w,$h) return $null } }

MakeArmorLayer "$armorRoot\robe_iron_layer_1.png"     @(150,160,178) @(215,220,230)
MakeArmorLayer "$armorRoot\robe_amethyst_layer_1.png" @(150,92,200)  @(215,175,242)
MakeArmorLayer "$armorRoot\robe_emerald_layer_1.png"  @(60,172,92)   @(175,238,185)
MakeArmorLayer "$armorRoot\robe_diamond_layer_1.png"  @(92,202,226)  @(205,242,250)
foreach($n in "robe_iron","robe_amethyst","robe_emerald","robe_diamond"){ MakeEmptyLayer "$armorRoot\${n}_layer_2.png" }

# --- fragment of law: a glowing violet shard inscribed with unreadable runes ---
MakeGraphics 16 16 "$I\fragment_of_law.png" {
    param($g,$w,$h)
    function PF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }
    $shard=@((PF 7 1),(PF 12 6),(PF 10 14),(PF 4 13),(PF 3 6))
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,86,40,150))),$shard)
    # facet shading
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,130,70,205))),@((PF 7 1),(PF 3 6),(PF 4 13),(PF 7 8)))
    # rune marks
    $rune=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(235,210,180,255)),1.0
    $g.DrawLine($rune,([float]6),([float]4),([float]6),([float]11))
    $g.DrawLine($rune,([float]6),([float]6),([float]9),([float]6))
    $g.DrawLine($rune,([float]6),([float]9),([float]9),([float]9))
    $g.DrawLine($rune,([float]9),([float]4),([float]9),([float]11))
    # bright outline
    $g.DrawPolygon((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,210,180,255)),1.0),$shard)
}

Write-Host "Item (v2: mana dust / vial / focus) ->"

# --- mana dust: a sparkling pile of blue powder ---
MakeGraphics 16 16 "$I\mana_dust.png" {
    param($g,$w,$h)
    function PF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }
    $pile=@((PF 2 13),(PF 5 9),(PF 8 7),(PF 11 9),(PF 14 13),(PF 14 14),(PF 2 14))
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,64,138,226))),$pile)
    # lighter crest along the top of the pile
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,120,196,255))),
        @((PF 5 9),(PF 8 7),(PF 11 9),(PF 9 11),(PF 7 11)))
    # loose grains around the pile
    $grain=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,150,215,255))
    foreach($p in @(@(3,11),@(12,10),@(6,6),@(10,5),@(14,11))){ $g.FillRectangle($grain,$p[0],$p[1],1,1) }
    # white sparkles
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(240,255,255,255))
    $g.FillRectangle($spark,8,3,1,3); $g.FillRectangle($spark,7,4,3,1)
    $g.FillRectangle($spark,12,7,1,1); $g.FillRectangle($spark,4,8,1,1)
}

# --- mana vial: a corked flask of glowing blue liquid ---
MakeGraphics 16 16 "$I\mana_vial.png" {
    param($g,$w,$h)
    # cork + neck
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,150,108,64))),6,1,4,2)
    $glassPen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,205,235,255)),1.0
    $g.DrawRectangle($glassPen,6,3,3,2)
    # body outline (round-bottom flask)
    $g.DrawEllipse($glassPen,3,5,9,10)
    # glowing liquid filling the bulb
    $g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,52,128,235))),4,7,8,7)
    $g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,110,190,255))),5,8,4,3)
    # rim light + sparkle
    $g.DrawArc((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(200,235,250,255)),1.0),4,6,7,8,200,90)
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(235,255,255,255))
    $g.FillRectangle($spark,10,9,1,1)
}

# --- arcane focus: a crystal-tipped wand wrapped in arcane yarn ---
MakeGraphics 16 16 "$I\arcane_focus.png" {
    param($g,$w,$h)
    # wooden shaft, bottom-left to upper-right
    $wood=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,124,84,48)),2.4
    $g.DrawLine($wood,([float]2),([float]14),([float]10),([float]6))
    $woodHi=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,168,120,72)),1.0
    $g.DrawLine($woodHi,([float]3),([float]13),([float]10),([float]6))
    # yarn wrap near the grip
    $wrap=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,120,210,255)),1.0
    $g.DrawLine($wrap,([float]4),([float]14),([float]6),([float]12))
    $g.DrawLine($wrap,([float]3),([float]13),([float]5),([float]11))
    # crystal tip
    function PF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }
    $gem=@((PF 11 1),(PF 14 4),(PF 12 7),(PF 9 4))
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,88,170,240))),$gem)
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,150,215,255))),@((PF 11 1),(PF 9 4),(PF 12 7)))
    $g.DrawPolygon((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,205,240,255)),1.0),$gem)
    # glow motes around the tip
    $mote=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(220,255,255,255))
    $g.FillRectangle($mote,14,1,1,1); $g.FillRectangle($mote,8,2,1,1); $g.FillRectangle($mote,14,7,1,1)
}

Write-Host "GUI (v2 spell icons) ->"
New-Item -ItemType Directory -Force -Path "$G\spell" | Out-Null

# Spell icon: dark disc + accent ring + a glyph drawn by $glyph (16x16, HUD selector).
function MakeSpellIcon([string]$name,$accent,[scriptblock]$glyph){
    MakeGraphics 16 16 "$G\spell\$name.png" {
        param($g,$w,$h)
        $g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(215,14,14,26))),0,0,15,15)
        $ring=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,$accent[0],$accent[1],$accent[2])),1.0
        $g.DrawEllipse($ring,0,0,15,15)
        & $glyph $g $accent
    }
}
function IconBrush($c,[int]$a=255){ return New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb($a,$c[0],$c[1],$c[2])) }
function IconPen($c,[double]$wd=1.2,[int]$a=255){ return New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($a,$c[0],$c[1],$c[2])),$wd }
function IPF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }

# bolt: a comet streaking down-left with a bright head
MakeSpellIcon "icon_bolt" @(140,220,255) {
    param($g,$c)
    $g.DrawLine((IconPen $c 1.6 200),([float]4),([float]12),([float]10),([float]6))
    $g.FillEllipse((IconBrush @(255,255,255)),8,3,5,5)
    $g.FillEllipse((IconBrush $c),9,4,3,3)
}
# spear: a golden lance with a leaf head
MakeSpellIcon "icon_spear" @(255,216,120) {
    param($g,$c)
    $g.DrawLine((IconPen @(230,200,140) 1.4),([float]4),([float]12),([float]11),([float]5))
    $g.FillPolygon((IconBrush @(255,255,255)),@((IPF 12 3),(IPF 13 7),(IPF 9 6)))
}
# fireball: a flame teardrop
MakeSpellIcon "icon_fireball" @(255,150,60) {
    param($g,$c)
    $g.FillPolygon((IconBrush $c),@((IPF 8 2),(IPF 12 9),(IPF 8 13),(IPF 4 9)))
    $g.FillPolygon((IconBrush @(255,236,150)),@((IPF 8 5),(IPF 10 9),(IPF 8 12),(IPF 6 9)))
}
# slow: an hourglass
MakeSpellIcon "icon_slow" @(190,140,255) {
    param($g,$c)
    $p=IconPen $c 1.2
    $g.DrawLine($p,([float]5),([float]3),([float]11),([float]3)); $g.DrawLine($p,([float]5),([float]13),([float]11),([float]13))
    $g.DrawLine($p,([float]5),([float]3),([float]11),([float]13)); $g.DrawLine($p,([float]11),([float]3),([float]5),([float]13))
    $g.FillRectangle((IconBrush @(255,255,255) 220),7,7,2,2)
}
# orb: a void sphere with a magenta rim
MakeSpellIcon "icon_orb" @(216,80,255) {
    param($g,$c)
    $g.FillEllipse((IconBrush @(30,10,45)),4,4,8,8)
    $g.DrawEllipse((IconPen $c 1.3),4,4,8,8)
    $g.FillRectangle((IconBrush @(255,255,255) 200),6,6,1,1)
}
# anti: a cleansing burst (X + ring)
MakeSpellIcon "icon_anti" @(110,255,235) {
    param($g,$c)
    $p=IconPen $c 1.3
    $g.DrawEllipse($p,4,4,8,8)
    $g.DrawLine($p,([float]3),([float]3),([float]13),([float]13)); $g.DrawLine($p,([float]13),([float]3),([float]3),([float]13))
}
# wind: three gusting waves
MakeSpellIcon "icon_wind" @(170,255,200) {
    param($g,$c)
    $p=IconPen $c 1.3
    $g.DrawArc($p,3,2,8,5,180,180); $g.DrawArc($p,4,6,9,5,180,180); $g.DrawArc($p,3,10,7,4,180,180)
}
# thunder: a lightning zigzag
MakeSpellIcon "icon_thunder" @(255,232,110) {
    param($g,$c)
    $g.FillPolygon((IconBrush $c),@((IPF 9 2),(IPF 5 8),(IPF 8 8),(IPF 6 14),(IPF 12 7),(IPF 9 7)))
}
# barrier: a hexagon shield
MakeSpellIcon "icon_barrier" @(120,220,255) {
    param($g,$c)
    $hex=@((IPF 8 2),(IPF 13 5),(IPF 13 11),(IPF 8 14),(IPF 3 11),(IPF 3 5))
    $g.DrawPolygon((IconPen $c 1.3),$hex)
    $g.DrawPolygon((IconPen $c 0.8 130),@((IPF 8 5),(IPF 11 7),(IPF 11 10),(IPF 8 12),(IPF 5 10),(IPF 5 7)))
}
# dash: double chevrons
MakeSpellIcon "icon_dash" @(235,235,255) {
    param($g,$c)
    $p=IconPen $c 1.5
    $g.DrawLine($p,([float]4),([float]4),([float]8),([float]8)); $g.DrawLine($p,([float]4),([float]12),([float]8),([float]8))
    $g.DrawLine($p,([float]8),([float]4),([float]12),([float]8)); $g.DrawLine($p,([float]8),([float]12),([float]12),([float]8))
}
# blade: a crescent slash
MakeSpellIcon "icon_blade" @(190,255,215) {
    param($g,$c)
    $g.DrawArc((IconPen $c 1.8),2,2,12,12,300,120)
    $g.DrawArc((IconPen @(255,255,255) 1.0 220),4,4,8,8,300,120)
}

Write-Host "Item (v2.1: tomes / wand-making) ->"
New-Item -ItemType Directory -Force -Path "$root\block" | Out-Null
$B = "$root\block"

# --- magic tome: a rune-sealed book, cover tinted per element ---
function MakeTome([string]$name,$cover,$accent){
    MakeGraphics 16 16 "$I\$name.png" {
        param($g,$w,$h)
        # pages block behind the cover (right edge)
        $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,222,214,186))),12,3,2,11)
        # cover
        $coverB=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,$cover[0],$cover[1],$cover[2]))
        $g.FillRectangle($coverB,2,2,11,12)
        # spine (darker strip on the left)
        $spine=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,
            [int]($cover[0]*0.55),[int]($cover[1]*0.55),[int]($cover[2]*0.55)))
        $g.FillRectangle($spine,2,2,2,12)
        # metal clasp
        $clasp=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,205,190,120))
        $g.FillRectangle($clasp,11,7,3,2)
        # rune sigil on the cover, in the accent colour
        $rune=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,$accent[0],$accent[1],$accent[2])),1.0
        $g.DrawEllipse($rune,5,4,6,6)
        $g.DrawLine($rune,([float]8),([float]4),([float]8),([float]10))
        $g.DrawLine($rune,([float]5),([float]7),([float]11),([float]7))
        # cover edge highlight
        $g.DrawRectangle((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(140,255,255,255)),0.8),2,2,11,12)
    }
}
MakeTome "tome_fire"  @(150,52,32)  @(255,190,90)
MakeTome "tome_water" @(38,84,158)  @(150,215,255)
MakeTome "tome_earth" @(96,72,40)   @(190,235,130)
MakeTome "tome_wind"  @(52,124,92)  @(200,255,220)
MakeTome "tome_misc"  @(88,58,132)  @(225,190,255)

# --- mana liquid: a corked upright bottle of dense glowing blue ---
MakeGraphics 16 16 "$I\mana_liquid.png" {
    param($g,$w,$h)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,150,108,64))),7,0,3,2)
    $glassPen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,205,235,255)),1.0
    $g.DrawRectangle($glassPen,6,2,4,3)
    $g.DrawRectangle($glassPen,4,5,8,10)
    # dense liquid nearly to the top
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,42,96,215))),5,6,7,9)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,96,170,255))),6,7,3,4)
    # sparkle motes suspended in it
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(235,255,255,255))
    $g.FillRectangle($spark,9,9,1,1); $g.FillRectangle($spark,6,12,1,1); $g.FillRectangle($spark,10,13,1,1)
}

# --- arcane wood: a plank tile recoloured to blue crystal ---
MakeGraphics 16 16 "$I\arcane_wood.png" {
    param($g,$w,$h)
    $base=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,62,110,190))
    $g.FillRectangle($base,0,0,16,16)
    # plank rows with alternating grain shade
    $dark=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,38,72,138)),1.0
    $light=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,110,170,240)),1.0
    foreach($y in 0,4,8,12){ $g.DrawLine($dark,([float]0),([float]$y),([float]15),([float]$y)) }
    foreach($y in 2,6,10,14){ $g.DrawLine($light,([float]1),([float]$y),([float]14),([float]$y)) }
    # staggered plank ends
    $g.DrawLine($dark,([float]5),([float]0),([float]5),([float]3)); $g.DrawLine($dark,([float]11),([float]4),([float]11),([float]7))
    $g.DrawLine($dark,([float]3),([float]8),([float]3),([float]11)); $g.DrawLine($dark,([float]9),([float]12),([float]9),([float]15))
    # crystal glints
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200,220,245,255))
    $g.FillRectangle($spark,7,1,1,1); $g.FillRectangle($spark,13,9,1,1); $g.FillRectangle($spark,2,13,1,1)
}

# --- arcane glue: a dripping pale blob flecked with glow ---
MakeGraphics 16 16 "$I\arcane_glue.png" {
    param($g,$w,$h)
    $blob=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(235,175,225,235))
    $g.FillEllipse($blob,3,4,10,8)
    $g.FillEllipse($blob,5,10,3,4)   # drip
    $g.FillEllipse($blob,10,10,2,3)  # drip
    $hi=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,225,250,255))
    $g.FillEllipse($hi,5,5,4,3)
    $fleck=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,240,150))
    $g.FillRectangle($fleck,9,7,1,1); $g.FillRectangle($fleck,6,9,1,1); $g.FillRectangle($fleck,11,5,1,1)
    $g.DrawEllipse((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(160,120,190,215)),1.0),3,4,10,8)
}

Write-Host "Block (arcane station) ->"

# --- arcane station top: a draped satin cloth with folds, sheen and the station sigil ---
MakeGraphics 16 16 "$B\arcane_station_top.png" {
    param($g,$w,$h)
    # satin base
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,150,112,196))),0,0,16,16)
    # diagonal fold shading (darker) and sheen (lighter) bands
    $fold=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,118,84,160)),1.0
    $sheen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,196,164,230)),1.0
    foreach($o in 2,8,14){ $g.DrawLine($fold,([float]$o),([float]0),([float]0),([float]$o)) }
    foreach($o in 5,11){ $g.DrawLine($sheen,([float]$o),([float]0),([float]0),([float]$o)) }
    foreach($o in 4,10){ $g.DrawLine($fold,([float]15),([float]$o),([float]$o),([float]15)) }
    foreach($o in 7,13){ $g.DrawLine($sheen,([float]15),([float]$o),([float]$o),([float]15)) }
    # stitched hem around the edge
    $hem=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,225,205,245)),1.0
    $g.DrawRectangle($hem,0,0,15,15)
    # embroidered station sigil (circle + cross) in silver thread
    $rune=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,235,240,255)),1.0
    $g.DrawEllipse($rune,4,4,7,7)
    $g.DrawLine($rune,([float]8),([float]2),([float]8),([float]13))
    $g.DrawLine($rune,([float]2),([float]8),([float]13),([float]8))
}

# --- arcane station side: plank body with the satin cloth overhanging the top edge ---
MakeGraphics 16 16 "$B\arcane_station_side.png" {
    param($g,$w,$h)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,52,94,166))),0,0,16,16)
    # satin overhang: a scalloped cloth edge hanging over the top third
    $satin=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,150,112,196))
    $g.FillRectangle($satin,0,0,16,4)
    foreach($x in 0,4,8,12){ $g.FillRectangle($satin,$x,4,3,2) } # scallops
    $satinDark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,118,84,160))
    $g.FillRectangle($satinDark,0,3,16,1)
    $hem=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,225,205,245))
    foreach($x in 0,4,8,12){ $g.FillRectangle($hem,$x,5,3,1) }
    # plank grain below the cloth
    $dark=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,34,64,122)),1.0
    foreach($y in 9,13){ $g.DrawLine($dark,([float]0),([float]$y),([float]15),([float]$y)) }
    $g.DrawLine($dark,([float]6),([float]6),([float]6),([float]8)); $g.DrawLine($dark,([float]12),([float]10),([float]12),([float]12))
    # crystal glints in the wood
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200,220,245,255))
    $g.FillRectangle($spark,3,11,1,1); $g.FillRectangle($spark,10,7,1,1)
}

Write-Host "GUI (arcane station + firebolt icon) ->"

# --- arcane station container GUI (176x166, vanilla container style; v2.2 layout with result slot) ---
MakeGraphics 176 166 "$G\arcane_station.png" {
    param($g,$w,$h)
    function SunkenSlot($g,$x,$y){
        # 18x18 sunken slot box (vanilla look)
        $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,139,139,139))),$x,$y,18,18)
        $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,55,55,55))),$x,$y,18,1)
        $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,55,55,55))),$x,$y,1,18)
        $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,255,255))),$x,($y+17),18,1)
        $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,255,255))),($x+17),$y,1,18)
    }
    # panel body + 3D border
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,198,198,198))),0,0,$w,$h)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,255,255))),0,0,$w,2)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,255,255))),0,0,2,$h)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,85,85,85))),0,($h-2),$w,2)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,85,85,85))),($w-2),0,2,$h)
    # satin banner strip under the title (matches the block's cloth top)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,150,112,196))),7,15,162,3)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,225,205,245))),7,18,162,1)
    # input slots A glue / B material / C core / D crystal (boxes at x=25/53/81/109, y=34)
    foreach($x in 25,53,81,109){ SunkenSlot $g $x 34 }
    # result slot (box at 147,34) with an arrow pointing into it
    SunkenSlot $g 147 34
    $arrow=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,110,110,120))
    $g.FillRectangle($arrow,129,41,12,4)
    foreach($i in 0..3){ $g.FillRectangle($arrow,(141+$i),(39+$i),1,(8-2*$i)) }
    # connective runes between the input slots
    $rune=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,120,160,230)),1.0
    foreach($x in 44,72,100){ $g.DrawLine($rune,([float]$x),([float]43),([float]($x+8)),([float]43)) }
    # faint hint glyphs under each input slot: droplet / rod / star / gem
    $hint=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,150,150,160)),1.0
    $g.DrawEllipse($hint,31,56,5,5)                                                              # A: glue droplet
    $g.DrawLine($hint,([float]59),([float]61),([float]65),([float]55))                           # B: material rod
    $g.DrawLine($hint,([float]90),([float]55),([float]90),([float]61)); $g.DrawLine($hint,([float]87),([float]58),([float]93),([float]58)); $g.DrawLine($hint,([float]88),([float]56),([float]92),([float]60)); $g.DrawLine($hint,([float]92),([float]56),([float]88),([float]60)) # C: core star
    $g.DrawLine($hint,([float]115),([float]55),([float]118),([float]61)); $g.DrawLine($hint,([float]118),([float]61),([float]121),([float]55)); $g.DrawLine($hint,([float]115),([float]55),([float]121),([float]55)) # D: crystal
    # player inventory (3 rows at y=83, hotbar at y=141), standard x=7..
    for($row=0;$row -lt 3;$row++){ for($col=0;$col -lt 9;$col++){ SunkenSlot $g (7+$col*18) (83+$row*18) } }
    for($col=0;$col -lt 9;$col++){ SunkenSlot $g (7+$col*18) 141 }
}

# firebolt: a blazing comet (the new single-target Fireball)
MakeSpellIcon "icon_firebolt" @(255,120,40) {
    param($g,$c)
    $g.DrawLine((IconPen @(255,200,120) 1.6 200),([float]3),([float]12),([float]9),([float]6))
    $g.FillEllipse((IconBrush $c),7,3,6,6)
    $g.FillEllipse((IconBrush @(255,236,160)),9,4,3,3)
}

Write-Host "Item (v2.2: wand + crystal cores) ->"

# --- wand base: a near-white shaft, tinted in-game by its material colour ---
MakeGraphics 16 16 "$I\wand_base.png" {
    param($g,$w,$h)
    # main shaft, bottom-left to upper-right (drawn bright so the material tint shows true)
    $shaft=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,225,225,225)),2.6
    $g.DrawLine($shaft,([float]2),([float]14),([float]12),([float]4))
    # shading edge + highlight for a rounded read
    $shade=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,168,168,168)),1.0
    $g.DrawLine($shade,([float]3),([float]15),([float]13),([float]5))
    $hi=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,255,255,255)),1.0
    $g.DrawLine($hi,([float]2),([float]13),([float]11),([float]4))
    # grip wrap notches near the base
    $wrap=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,150,150,150)),1.0
    $g.DrawLine($wrap,([float]3),([float]15),([float]5),([float]13))
    $g.DrawLine($wrap,([float]2),([float]14),([float]4),([float]12))
    # rounded tip cap
    $g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,235,235,235))),11,3,3,3)
}

# --- wand tip: a small near-white crystal at the tip, tinted by the crystal colour ---
MakeGraphics 16 16 "$I\wand_tip.png" {
    param($g,$w,$h)
    function PF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }
    $gem=@((PF 12 0),(PF 15 3),(PF 13 6),(PF 10 3))
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,225,225,225))),$gem)
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,255,255))),@((PF 12 0),(PF 10 3),(PF 13 6)))
    $g.DrawPolygon((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,170,170,170)),1.0),$gem)
    # sparkle
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(240,255,255,255))
    $g.FillRectangle($spark,15,0,1,1); $g.FillRectangle($spark,9,1,1,1)
}

# --- glow crystal: a radiant yellow-white gem ---
MakeGraphics 16 16 "$I\glow_crystal.png" {
    param($g,$w,$h)
    function PF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }
    # soft glow halo
    $g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(70,255,235,140))),1,1,14,14)
    $gem=@((PF 8 1),(PF 13 6),(PF 11 13),(PF 5 13),(PF 3 6))
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,245,215,105))),$gem)
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,245,190))),@((PF 8 1),(PF 3 6),(PF 5 13),(PF 8 8)))
    $g.DrawPolygon((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,255,250,220)),1.0),$gem)
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,255,255))
    $g.FillRectangle($spark,7,4,1,3); $g.FillRectangle($spark,6,5,3,1)
}

# --- dragon crystal: a magenta gem wreathed in dragon's-breath swirl ---
MakeGraphics 16 16 "$I\dragon_crystal.png" {
    param($g,$w,$h)
    function PF($x,$y){ return New-Object System.Drawing.PointF([float]$x,[float]$y) }
    $gem=@((PF 8 1),(PF 13 6),(PF 11 13),(PF 5 13),(PF 3 6))
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,168,52,190))),$gem)
    $g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,215,120,235))),@((PF 8 1),(PF 3 6),(PF 5 13),(PF 8 8)))
    $g.DrawPolygon((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,240,180,255)),1.0),$gem)
    # breath swirl arcs around the gem
    $swirl=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(200,235,120,215)),1.0
    $g.DrawArc($swirl,0,2,10,10,120,140)
    $g.DrawArc($swirl,6,4,10,10,300,140)
    $spark=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,255,235,255))
    $g.FillRectangle($spark,9,3,1,2); $g.FillRectangle($spark,4,10,1,1)
}

Write-Host "GUI (v2.2.1 spellbook) ->"

# --- open spellbook background (340x220): leather cover, two parchment pages, center spine ---
MakeGraphics 340 220 "$G\spellbook_bg.png" {
    param($g,$w,$h)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    # soft drop shadow behind the book
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(90,0,0,0))),10,12,326,204)
    # leather cover (dark brown) with a lighter bevel edge
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,60,40,26))),4,6,332,208)
    $g.DrawRectangle((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,96,66,40)),2.0),5,7,330,206)
    $g.DrawRectangle((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,38,24,14)),1.0),4,6,331,207)
    # gilt inner frame
    $g.DrawRectangle((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255,150,120,64)),1.0),12,12,316,196)

    # two parchment pages
    $pageBrush=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,232,220,188))
    $g.FillRectangle($pageBrush,16,15,150,190)   # left page  (16..166)
    $g.FillRectangle($pageBrush,174,15,150,190)  # right page (174..324)

    # page aging: subtle vignette shading toward the outer edges + top/bottom
    for($i=0;$i -lt 14;$i++){
        $a=[int](60*(1.0-$i/14.0))
        $shade=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($a,150,132,96)),1.0
        $g.DrawLine($shade,([float](16+$i)),([float]15),([float](16+$i)),([float]205))     # left outer
        $g.DrawLine($shade,([float](323-$i)),([float]15),([float](323-$i)),([float]205))   # right outer
    }
    for($i=0;$i -lt 8;$i++){
        $a=[int](50*(1.0-$i/8.0))
        $shade=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($a,150,132,96)),1.0
        $g.DrawLine($shade,([float]16),([float](15+$i)),([float]324),([float](15+$i)))     # top
        $g.DrawLine($shade,([float]16),([float](204-$i)),([float]324),([float](204-$i)))   # bottom
    }

    # center spine: darker gutter with page-curl shadows on both inner edges
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,74,52,32))),166,13,8,192)
    for($i=0;$i -lt 10;$i++){
        $a=[int](70*(1.0-$i/10.0))
        $sh=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($a,120,104,76)),1.0
        $g.DrawLine($sh,([float](165-$i)),([float]15),([float](165-$i)),([float]205))
        $g.DrawLine($sh,([float](175+$i)),([float]15),([float](175+$i)),([float]205))
    }
    # a couple of stitched headbands top and bottom of the spine
    $stitch=New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,150,120,64))
    $g.FillRectangle($stitch,166,13,8,3); $g.FillRectangle($stitch,166,202,8,3)

    # faint ruling lines on each page (guide the text rows)
    $rule=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(40,120,96,60)),1.0
    for($y=42;$y -le 196;$y+=11){
        $g.DrawLine($rule,([float]24),([float]$y),([float]158),([float]$y))
        $g.DrawLine($rule,([float]182),([float]$y),([float]316),([float]$y))
    }
    # page header underlines
    $hl=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(150,120,90,50)),1.0
    $g.DrawLine($hl,([float]24),([float]33),([float]158),([float]33))
    $g.DrawLine($hl,([float]182),([float]33),([float]316),([float]33))
}

Write-Host "Done. Textures in $root"
