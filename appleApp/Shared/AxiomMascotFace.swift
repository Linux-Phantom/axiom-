import SwiftUI

struct AxiomMascotFace: View {
    let mood: AxiomMood
    var size: CGFloat = 112

    @State private var isBreathing = false

    private var accent: Color {
        switch mood {
        case .happy: Color(red: 0.12, green: 0.66, blue: 0.55)
        case .anxious: Color(red: 0.93, green: 0.40, blue: 0.22)
        case .sad: Color(red: 0.34, green: 0.49, blue: 0.78)
        }
    }

    var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .fill(.white)
                .overlay {
                    RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                        .strokeBorder(accent.opacity(0.22), lineWidth: 2)
                }

            VStack(spacing: size * 0.13) {
                Capsule()
                    .fill(accent)
                    .frame(width: size * 0.08, height: size * 0.18)
                    .overlay(alignment: .top) {
                        Circle()
                            .fill(accent)
                            .frame(width: size * 0.15, height: size * 0.15)
                            .offset(y: -size * 0.07)
                    }

                HStack(spacing: size * 0.25) {
                    eye
                    eye
                }
                .padding(.top, size * 0.05)

                mouth
                    .stroke(accent, style: StrokeStyle(lineWidth: max(2, size * 0.045), lineCap: .round))
                    .frame(width: size * 0.32, height: size * 0.14)
            }
            .padding(.top, size * 0.14)
        }
        .frame(width: size, height: size * 0.92)
        .background(accent.opacity(0.12), in: RoundedRectangle(cornerRadius: size * 0.32, style: .continuous))
        .scaleEffect(mood == .anxious && isBreathing ? 1.035 : 1)
        .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: isBreathing)
        .onAppear { isBreathing = true }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Axiom, настроение: \(mood.title)")
    }

    private var eye: some View {
        Circle()
            .fill(Color(red: 0.12, green: 0.17, blue: 0.20))
            .frame(width: size * 0.09, height: size * 0.09)
    }

    private var mouth: Path {
        Path { path in
            if mood == .anxious {
                path.addEllipse(in: CGRect(x: 0, y: 0, width: size * 0.32, height: size * 0.14))
            } else {
                path.move(to: CGPoint(x: 0, y: size * 0.02))
                let controlY = mood == .happy ? size * 0.20 : -size * 0.12
                path.addQuadCurve(
                    to: CGPoint(x: size * 0.32, y: size * 0.02),
                    control: CGPoint(x: size * 0.16, y: controlY)
                )
            }
        }
    }
}