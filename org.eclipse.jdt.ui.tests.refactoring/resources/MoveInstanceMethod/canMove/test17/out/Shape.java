package p;

public class Shape {
	Rectangle fBounds;
	Rectangle fInnerBounds;
	
	public int area() {
		return fBounds.area();
	}
	
	public int filledArea() {
		return area() -	fInnerBounds.getWidth() * fInnerBounds.getHeight();
	}
	
	public boolean isSmallerThan(Rectangle rect) {
		return area() < rect.getWidth() * rect.getHeight();
	}
	
}
